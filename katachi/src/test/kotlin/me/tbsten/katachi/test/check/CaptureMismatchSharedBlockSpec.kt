package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.CaptureMismatch
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.kotlin.ktFile

class CaptureMismatchSharedBlockSpec : FreeSpec({
    val definition = architectureOf {
        "feature".group {
            "Component".role {
                layout {
                    "feature" / capture("feature") / "src" / capture("feature") / "component" / capture("name").ktFile()
                }
            }
        }
    }

    "間違えたディレクトリの配下のファイルは 1 つのブロックにまとまり、ほかのファイルが並ぶ" {
        val tree = repositoryOf { "feature/home/src/settings/component" { "A.kt"(); "B.kt"(); "C.kt"() } }
        val report = definition.validate(tree).report()

        report.lines().first() shouldBe "Katachi check failed: 3 violations (Unexpected: 3)"
        report.lines().count { it.startsWith("[CaptureMismatch]") } shouldBe 1
        report shouldContain "  2 more files fail the same way:"
        report shouldContain "    - feature/home/src/settings/component/B.kt"
        report shouldContain "    - feature/home/src/settings/component/C.kt"
    }

    "並べるのは 5 ファイルまでで、残りは件数にする" {
        val tree = repositoryOf {
            "feature/home/src/settings/component" { (1..8).forEach { "F$it.kt"() } }
        }
        val report = definition.validate(tree).report()

        report shouldContain "  7 more files fail the same way:"
        report.lines().count { it.startsWith("    - feature/home/src/settings/component/") } shouldBe 5
        report shouldContain "    - and 2 more"
    }

    "食い違ったディレクトリが違えば、別のブロックになる" {
        val tree = repositoryOf {
            "feature/home/src/settings/component" { "A.kt"() }
            "feature/home/src/other/component" { "B.kt"() }
        }
        val report = definition.validate(tree).report()

        report.lines().count { it.startsWith("[CaptureMismatch]") } shouldBe 2
        report.contains("fail the same way") shouldBe false
    }

    "ブロックにまとまっても、違反は 1 ファイルにつき 1 件のまま" {
        val tree = repositoryOf { "feature/home/src/settings/component" { "A.kt"(); "B.kt"(); "C.kt"() } }

        definition.validate(tree).filterIsInstance<CaptureMismatch>().map { it.path } shouldBe listOf(
            "feature/home/src/settings/component/A.kt",
            "feature/home/src/settings/component/B.kt",
            "feature/home/src/settings/component/C.kt",
        )
    }

    "ブロックの予算は、まとまったブロックを 1 つと数える" {
        val tree = repositoryOf {
            "feature/home/src/settings/component" { (1..10).forEach { "F$it.kt"() } }
            "feature/home/src/other/component" { "B.kt"() }
        }
        val report = definition.validate(tree).report(maxViolations = 2)

        report.lines().count { it.startsWith("[CaptureMismatch]") } shouldBe 2
        report.lines().none { it.startsWith("Showing first") } shouldBe true
    }
})
