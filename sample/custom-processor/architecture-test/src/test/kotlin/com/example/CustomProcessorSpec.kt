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
    "引数を取らない processor が、役割ごとのファイル数を返す" {
        val lines = projectArchitecture.process(RoleFileCount).getOrThrow()

        withClue(lines.joinToString("\n")) {
            lines.map { it.substringBefore(":") } shouldBe
                projectArchitecture.allRoles.map { it.qualifiedName }
            // `tool/Documentation` covers `README.md` and nothing else -- no `.module { }`,
            // so no `build.gradle.kts` is counted with it and the number does not move when
            // a source file is added elsewhere.
            lines shouldContain "tool.Documentation: 1 件"
        }
    }

    "型付き引数を取る processor が、group で絞り込んで並べ替えた表を返す" {
        val table = projectArchitecture.process(
            RoleTable,
            RoleTable.Args(
                title = "役割",
                groups = listOf("core"),
                sortBy = RoleTable.SortBy.Name,
            ),
        ).getOrThrow()

        withClue(table.joinToString("\n")) {
            table.take(2) shouldBe listOf("| 役割 | 概要 |", "|---|---|")
            table.drop(2).map { it.substringAfter("| ").substringBefore(" |") } shouldBe
                listOf("core.Entrypoint", "core.Model", "core.Store")
        }
    }

    "--arg のカンマは、受け取る側が List のときだけ分割される" {
        // The rule decodeFromStringMap documents, written out against this sample's own Args:
        // `title` is a String and keeps its comma, `groups` is a List and is split. There is
        // no escape syntax, so a String field is also the way to pass a value holding a comma.
        val args = decodeFromStringMap(
            RoleTable.Args.serializer(),
            mapOf(
                "title" to "役割,一覧",
                "groups" to "core,testing",
                "minExamples" to "2",
                "sortBy" to "Name",
            ),
        )

        args shouldBe RoleTable.Args(
            title = "役割,一覧",
            groups = listOf("core", "testing"),
            minExamples = 2,
            sortBy = RoleTable.SortBy.Name,
        )
    }

    "検査する processor が、この定義には問題を見つけず success を返す" {
        // getOrThrow() が投げれば、欠けている役割の一覧がそのまま失敗メッセージになる。
        val report = projectArchitecture.process(RoleDocCoverage).getOrThrow()

        withClue(report.toString()) {
            report.missing shouldBe emptyList()
            // `core` と `testing` の7役割だけが対象。`Gradle` と `tool` は documented = false。
            report.checked shouldBe 7
        }
    }

    "検査する processor が、summary と example を欠いた定義に failure を返す" {
        // Deliberately broken, and deliberately built here rather than in `ProjectArchitecture.kt`:
        // a reader looking for the definition to copy should never meet it.
        val broken = architecture {
            "core".group {
                title = "本体"
                summary = "壊れた定義"
                "Written" {
                    summary = "summary も example もある役割"
                    example("Note", "見出しと本文を持つノート")
                }
                "Blank" { }
            }
        }

        // A `success` here would make the run print `[OK]` and exit zero while holding the
        // two problems below -- a check that can never fail.
        val failure = broken.process(RoleDocCoverage).exceptionOrNull()
            .shouldBeInstanceOf<RoleDocCoverage.IncompleteDocumentation>()
        val report = failure.report

        withClue(report.toString()) {
            report.checked shouldBe 2
            report.missing shouldBe listOf(
                RoleDocCoverage.Missing(role = "core.Blank", reason = "summary が無い"),
                RoleDocCoverage.Missing(role = "core.Blank", reason = "example が1つも無い"),
            )
            // A `katachi<Key>` task prints the message under `[FAILED]`, so it is the report.
            failure.message shouldBe report.toString()
        }
    }

    "documented = false の group の役割は、検査する processor の対象から外れる" {
        // Metadata is not inherited, so walking up `groupPath` is the processor's own doing.
        // The role below writes neither `summary` nor `example` and is still not reported.
        val silent = architecture {
            "hidden".group {
                documented = false
                "Silent" { }
            }
        }

        val report = silent.process(RoleDocCoverage).getOrThrow()

        withClue(report.toString()) {
            report.checked shouldBe 0
            report.missing shouldBe emptyList()
        }
    }
})
