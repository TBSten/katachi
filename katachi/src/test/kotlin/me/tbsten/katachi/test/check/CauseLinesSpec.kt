package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.UncheckedCheck
import me.tbsten.katachi.check.UncheckedDirectory
import me.tbsten.katachi.check.UncheckedDirectoryReason
import me.tbsten.katachi.check.UncheckedFile
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.blockOf
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.report.internal.sarifMessageOf
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.check.FileConstraintCheck

/**
 * How a failure's message reaches a `Cause:` line of the report.
 *
 * The report is the one source the text report and the SARIF message are both made from, so what is
 * pinned here is what every one of them carries.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.check` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */
class CauseLinesSpec : FreeSpec({
    fun causeBlockOf(message: String?): List<String> {
        val arch = architectureOf {
            "domain".group {
                "UseCase".role {
                    layout {
                        "alpha" {
                            fileConstraint("invoke", check = throwing { IllegalStateException(message) })
                            "*.kt".file()
                        }
                    }
                }
            }
        }
        val report = arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck()).report()
        val lines = report.lines()
        val start = lines.indexOfFirst { it.startsWith("  Cause:") }
        return lines.drop(start).takeWhile { it.isNotEmpty() }
    }

    "message の 2 行目以降も Cause: の下に 1 段深く全部出る" {
        causeBlockOf("first\nsecond\n  third indented") shouldBe listOf(
            "  Cause: java.lang.IllegalStateException: first",
            "    second",
            "      third indented",
        )
    }

    "message の中の空行は出さない（空行はブロックの終わりを表すため）" {
        causeBlockOf("first\n\nsecond\n") shouldBe listOf(
            "  Cause: java.lang.IllegalStateException: first",
            "    second",
        )
    }

    "1 行だけの message は今までどおり 1 行" {
        causeBlockOf("only") shouldBe listOf("  Cause: java.lang.IllegalStateException: only")
    }

    "message が無いときは型だけが出る" {
        causeBlockOf(null) shouldBe listOf("  Cause: java.lang.IllegalStateException")
    }

    "Cause: を持つどの種類の違反でも、2 行目以降が 1 段深く出る" - {
        val cause = IllegalStateException("first\nsecond")
        val expected = listOf("  Cause: java.lang.IllegalStateException: first", "    second")
        val violations: Map<String, Violation> = mapOf(
            "UncheckedFile" to UncheckedFile(path = "a/A.kt", cause = cause),
            "UncheckedDirectory" to UncheckedDirectory(
                path = "build",
                reason = UncheckedDirectoryReason.NotWalked,
                cause = cause,
            ),
            "UncheckedCheck" to UncheckedCheck(check = "com.example.TodoCheck", cause = cause),
        )
        for ((kind, violation) in violations) {
            "$kind" {
                val block = blockOf(violation, root = null)
                val start = block.indexOfFirst { it.startsWith("  Cause:") }
                block.drop(start).take(expected.size) shouldBe expected
            }
        }
    }

    "SARIF の message でも、Cause: の 2 行目以降が 1 段深く載る" {
        val violation = UncheckedFile(path = "a/A.kt", cause = IllegalStateException("first\nsecond"))
        val lines = sarifMessageOf(violation).lines()
        lines.drop(lines.indexOfFirst { it.startsWith("Cause:") }).take(2) shouldBe listOf(
            "Cause: java.lang.IllegalStateException: first",
            "  second",
        )
    }
})
