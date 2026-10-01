package com.example

import com.example.processors.RoleDocCoverage
import com.example.processors.RoleFileCount
import com.example.processors.RoleTable
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.decodeFromStringMap
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.util.KatachiMultipleFailuresException

/**
 * The three processors of this sample, called the way a user calls them from a test.
 *
 * A `katachi<Key>` task is one of two doors and the slower one: it starts a JVM, resolves the
 * registry and decodes a command line. `projectArchitecture.process(...)` is the other, and it
 * is what a processor is developed against -- the result comes back typed, so an assertion can
 * read it instead of parsing a printed report.
 *
 * Registering a processor in `build.gradle.kts` is what gives it a `katachi<Key>` task; it is
 * not needed for anything in this file.
 */
@OptIn(ExperimentalKatachiApi::class)
class CustomProcessorSpec : FreeSpec({
    "a processor without arguments returns the file count of each role" {
        val lines = projectArchitecture.process(RoleFileCount).getOrThrow()

        withClue(lines.joinToString("\n")) {
            lines.map { it.substringBefore(":") } shouldBe
                projectArchitecture.allRoles.map { it.qualifiedName }
            // `tool/Documentation` covers `README.md` and nothing else, so the number does
            // not move when a source file is added elsewhere.
            lines shouldContain "tool.Documentation: 1 file(s)"
        }
    }

    "a processor with typed arguments returns a table narrowed by group and sorted" {
        val table = projectArchitecture.process(
            RoleTable,
            RoleTable.Args(
                title = "Role",
                groups = listOf("core"),
                sortBy = RoleTable.SortBy.Name,
            ),
        ).getOrThrow()

        withClue(table.joinToString("\n")) {
            table.take(2) shouldBe listOf("| Role | Summary |", "|---|---|")
            table.drop(2).map { it.substringAfter("| ").substringBefore(" |") } shouldBe
                listOf("core.Entrypoint", "core.Model", "core.Store")
        }
    }

    "a comma in --arg is split only when the receiving field is a List" {
        // The rule decodeFromStringMap documents, written out against this sample's own Args:
        // `title` is a String and keeps its comma, `groups` is a List and is split. There is
        // no escape syntax, so a String field is also the way to pass a value holding a comma.
        val args = decodeFromStringMap(
            RoleTable.Args.serializer(),
            mapOf(
                "title" to "Role,List",
                "groups" to "core,testing",
                "minExamples" to "2",
                "sortBy" to "Name",
            ),
        )

        args shouldBe RoleTable.Args(
            title = "Role,List",
            groups = listOf("core", "testing"),
            minExamples = 2,
            sortBy = RoleTable.SortBy.Name,
        )
    }

    "the checking processor finds no problem in this definition and returns success" {
        // If getOrThrow() throws, every gap is a suppressed exception of the failure.
        val report = projectArchitecture.process(RoleDocCoverage).getOrThrow()

        withClue(report.toString()) {
            // Only the 12 roles of `core` and `testing` are checked. `Gradle` and `tool` are documented = false.
            report.checked shouldBe 12
        }
    }

    "the checking processor returns failure for a definition without summary and example" {
        // Deliberately broken, and deliberately built here rather than in `ProjectArchitecture.kt`:
        // a reader looking for the definition to copy should never meet it.
        val broken = architecture {
            "core".group {
                title = "Application"
                summary = "A broken definition"
                "Written" {
                    summary = "A role with both a summary and an example"
                    example("Note", "A note with a title and a body")
                }
                "Blank" { }
            }
        }

        // A `success` here would make the run print `[OK]` and exit zero while holding the
        // two problems below -- a check that can never fail. Both are reported, not only the first.
        val failure = broken.process(RoleDocCoverage).exceptionOrNull()
            .shouldBeInstanceOf<KatachiMultipleFailuresException>()

        failure.suppressed.map { it.message } shouldBe listOf(
            "core.Blank: no summary",
            "core.Blank: no example",
        )
    }

    "a definition with no documented role stops the check at once" {
        val empty = architecture { }

        val failure = empty.process(RoleDocCoverage).exceptionOrNull()
            .shouldBeInstanceOf<IllegalStateException>()

        failure.message shouldBe "no documented roles"
    }

    "roles in a group with documented = false are left out of the checking processor" {
        // Metadata is not inherited, so walking up `groupPath` is the processor's own doing.
        // The role below writes neither `summary` nor `example` and is still not reported.
        val silent = architecture {
            "hidden".group {
                documented = false
                "Silent" { }
            }
        }

        // Nothing is documented, so the check has nothing to read and says so.
        val failure = silent.process(RoleDocCoverage).exceptionOrNull()
            .shouldBeInstanceOf<IllegalStateException>()

        failure.message shouldBe "no documented roles"
    }
})
