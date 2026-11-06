@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
@file:Suppress("DEPRECATION_ERROR")

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.ide.internal.DescribeDefinition
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.test.konsist.fixtureProject

/*
 * `DescribeDefinition` over a real `ktFileContent { }` and real files: the paths the definition
 * task has of its own, which the fake `ExplainByFileName` of `:katachi`'s golden does not walk --
 * the cut `files` handed to the constraint, the per-run memo and claim, the budget over `.kt`
 * files only, and a block that writes nothing.
 */

private const val SERVICE_DIRECTORY: String = "src/main/kotlin/com/example"

private fun kt(body: String): String = "package com.example\n\n" + body.trimIndent() + "\n"

/** One role whose directory holds `.kt` files and a `.txt` next to them, under one `ktFileContent { }`. */
private fun serviceArchitecture(block: KtFileContentScope.() -> Unit): Architecture = architecture {
    files = wholeTree()
    "Service".role {
        layout {
            "gradlew".file()
            SERVICE_DIRECTORY {
                "*".ktFile()
                "*.txt".file()
                ktFileContent(block)
            }
        }
    }
}

/** A role per wildcard module, whose block covers the module's `build.gradle.kts` before its sources. */
private fun moduleArchitecture(): Architecture = architecture {
    files = wholeTree()
    "Screen".role {
        layout {
            "gradlew".file()
            ":feature:*".module {
                "build.gradle.kts".file()
                mainSourceSet / kotlin / "*Screen".ktFile()
                ktFileContent { required(`class`, fileName) }
            }
        }
    }
}

/** What `DescribeDefinition` writes for [architecture] over [sources], with the arguments [args] given the output path. */
private fun describedJson(
    architecture: Architecture,
    vararg sources: Pair<String, String>,
    args: (String) -> DescribeDefinition.Args = { DescribeDefinition.Args(output = it) },
): String = fixtureProject(*sources) { root ->
    val output = File(root, "out/definition.json")
    architecture.process(DescribeDefinition, args(output.path), RealFileSystem(root)).getOrThrow()
    output.readText()
}

/** Each explained declaration of [json] as `<kind> <name> <line> <verdict>`, in the order written. */
private fun declarationsIn(json: String): List<String> =
    Regex(""""kind": "(\w+)",\s*"name": "([^"]*)",\s*"line": (\d+),\s*"verdict": "(\w+)"""")
        .findAll(json.substringAfter("\"fileDeclarations\": [", missingDelimiterValue = ""))
        .map { match ->
            val (kind, name, line, verdict) = match.destructured
            "$kind $name $line $verdict"
        }
        .toList()

/** The `problems` of [json] about explanations, by kind. */
private fun explanationProblemsIn(json: String): List<String> =
    Regex(""""kind": "(Explanation\w+)"""").findAll(json).map { it.groupValues[1] }.toList()

class DescribeDefinitionKtFileContentSpec : FreeSpec({
    val healthService = "$SERVICE_DIRECTORY/HealthService.kt" to kt("class HealthService\nfun stray() {}")
    val userService = "$SERVICE_DIRECTORY/UserService.kt" to kt("object UserService")
    val notes = "$SERVICE_DIRECTORY/notes.txt" to "not Kotlin"

    "fileDeclarations" - {
        "書かれた JSON は explainDeclarations の答えと同じ宣言・行・verdict で、.txt は読まれない" {
            val block: KtFileContentScope.() -> Unit = {
                required(`class`, fileName)
                allow(`object`, "*Factory")
            }
            val json = describedJson(serviceArchitecture(block), healthService, userService, notes)

            declarationsIn(json) shouldContainExactly listOf(
                "class HealthService 3 Allowed",
                "fun stray 4 Unexpected",
                "object UserService 3 Mismatched",
            )
            json shouldNotContain "\"file\": \"$SERVICE_DIRECTORY/notes.txt\""
            explanationProblemsIn(json).shouldBeEmpty()

            // The same sources and block, asked through a run of the check: the same three answers.
            val run = explainedRun(healthService, userService, notes, paths = { SERVICE_DIRECTORY { "*".ktFile(); "*.txt".file() } }, block = block)
            run.explanations.getOrThrow().flatMap { file ->
                file.declarations.map { "${it.kind} ${it.name} ${it.line} ${it.verdict.name}" }
            } shouldContainExactly declarationsIn(json)
        }

        "ktFileContent が module ブロックにあれば build.gradle.kts を先に並べても、各モジュールの .kt が説明される" {
            val json = describedJson(
                moduleArchitecture(),
                "feature/home/build.gradle.kts" to "",
                "feature/home/src/main/kotlin/HomeScreen.kt" to kt("class HomeScreen"),
                "feature/settings/build.gradle.kts" to "",
                "feature/settings/src/main/kotlin/SettingsScreen.kt" to kt("class SettingsScreen"),
            )

            declarationsIn(json) shouldContainExactly listOf(
                "class HomeScreen 3 Allowed",
                "class SettingsScreen 3 Allowed",
            )
            explanationProblemsIn(json).shouldBeEmpty()
        }
    }

    "予算" - {
        "maxExplainedFiles は .kt だけ数え、先に並んだ build.gradle.kts だけが残る切り方をしない" {
            val json = describedJson(
                moduleArchitecture(),
                "feature/home/build.gradle.kts" to "",
                "feature/home/src/main/kotlin/HomeScreen.kt" to kt("class HomeScreen"),
                "feature/settings/build.gradle.kts" to "",
                "feature/settings/src/main/kotlin/SettingsScreen.kt" to kt("class SettingsScreen"),
                args = { DescribeDefinition.Args(output = it, maxExplainedFiles = 1) },
            )

            // The first module explains its one `.kt` file within the budget; the second is cut, and
            // that is the one thing said -- no ExplanationFailed for a subject of `.kts` alone.
            declarationsIn(json) shouldContainExactly listOf("class HomeScreen 3 Allowed")
            explanationProblemsIn(json) shouldContainExactly listOf("ExplanationTruncated")
        }

        "落ちたのが .kt 以外だけなら ExplanationTruncated は出ない" {
            val json = describedJson(
                serviceArchitecture { required(`class`, fileName) },
                healthService,
                notes,
                args = { DescribeDefinition.Args(output = it, maxExplainedFiles = 1) },
            )

            declarationsIn(json) shouldContainExactly listOf("class HealthService 3 Allowed", "fun stray 4 Unexpected")
            explanationProblemsIn(json).shouldBeEmpty()
        }
    }

    "説明できないとき" - {
        "どのファイルにも行を書かないブロックは説明の失敗にならず、宣言をすべて許されないと答える" {
            val architecture = architecture {
                files = wholeTree()
                "Silent".role {
                    layout {
                        "gradlew".file()
                        "src/silent" { "*".ktFile(); ktFileContent { } }
                    }
                }
                "Loud".role {
                    layout { "src/loud" { "*".ktFile(); ktFileContent { required(`class`, fileName) } } }
                }
            }
            val json = describedJson(
                architecture,
                "src/silent/Quiet.kt" to kt("class Quiet"),
                "src/loud/Loud.kt" to kt("class Loud"),
            )

            explanationProblemsIn(json).shouldBeEmpty()
            declarationsIn(json) shouldContainExactly listOf("class Quiet 3 Unexpected", "class Loud 3 Allowed")
        }

        "宣言の行を書かないブロックも宣言を判定し、declarationsChecked = true ですべて許されないと答える" {
            val json = describedJson(serviceArchitecture { `import`("**") }, healthService)

            declarationsIn(json) shouldContainExactly listOf("class HealthService 3 Unexpected", "fun stray 4 Unexpected")
            json shouldContain "\"declarationsChecked\": true"
            json shouldContain "\"unexplainedReason\": null"
            json shouldNotContain "Konsist did not read"
        }

        "explainDeclarations = false なら Kotlin のソースを読まず fileDeclarations は null" {
            val json = describedJson(
                serviceArchitecture { required(`class`, fileName) },
                healthService,
                args = { DescribeDefinition.Args(output = it, explainDeclarations = false) },
            )

            declarationsIn(json).shouldBeEmpty()
            json shouldContain "\"fileDeclarations\": null"
            explanationProblemsIn(json) shouldBe emptyList()
        }
    }
})
