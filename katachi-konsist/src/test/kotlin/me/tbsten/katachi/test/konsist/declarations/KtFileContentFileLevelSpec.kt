package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.konsist.fixtureProject

/** What a file holds besides its declarations: file annotations, and the `actual` declarations its `expect` ones need. */

private fun kt(head: String, body: String = "class Sample"): String = head.trimIndent() + "\npackage com.example\n\n" + body + "\n"

/** The declarations, reasons included, that `ktFileContent { }` rejects in the `commonMain` file of a fixture project. */
@OptIn(InternalKatachiApi::class)
private fun rejected(vararg sources: Pair<String, String>, block: KtFileContentScope.() -> Unit): List<String> = fixtureProject(*sources) { root ->
    val definition = architecture {
        files = wholeTree()
        "Common".role {
            layout {
                "gradlew".file()
                "src" / "commonMain" / "kotlin" / "com" / "example" / "*".ktFile()
                "src" / "*Main" / "**".file()
            }
            ktFileContent(block = block)
        }
    }
    definition.validate(RealFileSystem(root), FileConstraintCheck())
        .filterIsInstance<UnsatisfiedFileConstraint>()
        .filter { "commonMain" in it.path }
        .map { violation -> "${violation.declaration} | " + violation.reasons.joinToString(" | ") { "${it.label}: ${it.text}" } }
}

class KtFileContentFileLevelSpec : FreeSpec({
    "ファイル注釈" - {
        "行の無いファイル注釈は許されない" {
            judged(kt("@file:Suppress(\"unused\")")) {
                `package`("**")
                allow(`class`, "Sample")
            } shouldBe listOf("@file:Suppress(\"unused\") (line 1) | Not allowed: ktFileContent { } allows no file annotation here")
        }

        "fileAnnotation(...) が許す。引数も書ける" {
            judged(kt("@file:Suppress(\"unused\")\n@file:JvmName(\"Names\")")) {
                `package`("**")
                fileAnnotation("Suppress(*unused*)")
                fileAnnotation("JvmName")
                allow(`class`, "Sample")
            }.shouldBeEmpty()
        }

        "not.fileAnnotation(...) は許可の行にも勝つ" {
            judged(kt("@file:Suppress(\"unused\")")) {
                `package`("**")
                fileAnnotation("*")
                not.fileAnnotation("Suppress")
                allow(`class`, "Sample")
            } shouldBe listOf("@file:Suppress(\"unused\") (line 1) | Not allowed: @file:Suppress(\"unused\") | Prohibited by: not @file:Suppress")
        }
    }

    "actualsIn(...)" - {
        val common = "src/commonMain/kotlin/com/example/Platform.kt" to
            "package com.example\n\nexpect fun platformName(): String\n\nexpect class Clock\n"

        "各ソースセットの同じディレクトリに actual があれば通る（Platform.android.kt も見る）" {
            rejected(
                common,
                "src/androidMain/kotlin/com/example/Platform.android.kt" to
                    "package com.example\n\nactual fun platformName(): String = \"Android\"\n\nactual class Clock\n",
                "src/iosMain/kotlin/com/example/Platform.kt" to
                    "package com.example\n\nactual fun platformName(): String = \"iOS\"\n\nactual class Clock\n",
            ) {
                `package`("**")
                allow(expect, anythingElse)
                actualsIn("androidMain", "iosMain")
            }.shouldBeEmpty()
        }

        "actual が無いソースセットごとに 1 件" {
            rejected(
                common,
                "src/androidMain/kotlin/com/example/Platform.android.kt" to "package com.example\n\nactual class Clock\n",
            ) {
                `package`("**")
                allow(expect, anythingElse)
                actualsIn("androidMain", "iosMain")
            } shouldBe listOf(
                "platformName | Missing actual: actual fun platformName(): String in androidMain | " +
                    "Looked in: src/androidMain/kotlin/com/example/Platform{,.*}.kt",
                "platformName | Missing actual: actual fun platformName(): String in iosMain | " +
                    "Looked in: src/iosMain/kotlin/com/example/Platform{,.*}.kt",
                "Clock | Missing actual: actual class Clock in iosMain | Looked in: src/iosMain/kotlin/com/example/Platform{,.*}.kt",
            )
        }

        "ソースセットを書かないと ActualsWithoutSourceSets" {
            shouldThrow<KatachiKtFileContentLineException> {
                judged(kt("")) { actualsIn() }
            }.problem shouldBe KtFileContentLineProblem.ActualsWithoutSourceSets
        }
    }
})
