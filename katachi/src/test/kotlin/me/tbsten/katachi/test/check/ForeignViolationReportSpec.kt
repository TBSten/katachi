package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.report
import me.tbsten.katachi.scan.Severity
import me.tbsten.katachi.scan.Violation
import me.tbsten.katachi.scan.ViolationDetail
import me.tbsten.katachi.scan.ViolationKind

/**
 * A [Violation] the way a third-party check would define one: no access to katachi's internal
 * constructors, only the public interface.
 */
private class MinimalViolation(
    override val path: String,
    override val label: String,
    override val kind: ViolationKind = ViolationKind.Unexpected,
    override val severity: Severity = Severity.Error,
) : Violation

/** The same shape, but overriding [Violation.details] the way a check that has values to state would. */
private class DetailedViolation(
    override val path: String,
    override val label: String,
    override val details: List<ViolationDetail>,
    override val kind: ViolationKind = ViolationKind.Unexpected,
    override val severity: Severity = Severity.Error,
) : Violation

/**
 * `blockOf`'s `else` branch (`foreignBlock`): how a violation katachi did not write renders.
 */
class ForeignViolationReportSpec : FreeSpec({
    "details を override した外部の Violation" - {
        "[Label] path の次に Label: value 行が STEP インデントで並ぶ" {
            val violation = DetailedViolation(
                path = "app/src/Foo.kt",
                label = "TodoRule",
                details = listOf(ViolationDetail("Rule", "TODO"), ViolationDetail("Line", "12")),
            )

            listOf(violation).report() shouldBe
                """
                Katachi check failed: 1 violation (Unexpected: 1)

                [TodoRule] app/src/Foo.kt
                  Rule: TODO
                  Line: 12
                """.trimIndent()
        }
    }

    "details を override しない外部の Violation" - {
        "1行ブロックに縮退する" {
            val violation = MinimalViolation(path = "app/src/Foo.kt", label = "TodoRule")

            listOf(violation).report() shouldBe
                """
                Katachi check failed: 1 violation (Unexpected: 1)

                [TodoRule] app/src/Foo.kt
                """.trimIndent()
        }
    }

    "改行と空文字の畳み込み" - {
        "label / path / detail のどこに改行を入れても1行に畳まれる" {
            val violation = DetailedViolation(
                path = "app/src/Foo.kt\nignored path line",
                label = "Todo\nRule\nignored label line",
                details = listOf(ViolationDetail("Rule\nignored", "  TODO  \nignored value line")),
            )

            listOf(violation).report() shouldBe
                """
                Katachi check failed: 1 violation (Unexpected: 1)

                [Todo] app/src/Foo.kt
                  Rule: TODO
                """.trimIndent()
        }

        "空文字は <empty> になる" {
            val violation = DetailedViolation(
                path = "app/src/Foo.kt",
                label = "TodoRule",
                details = listOf(ViolationDetail("", "")),
            )

            listOf(violation).report() shouldBe
                """
                Katachi check failed: 1 violation (Unexpected: 1)

                [TodoRule] app/src/Foo.kt
                  <empty>: <empty>
                """.trimIndent()
        }
    }

    "サマリ行" - {
        "外部の kind = Constraint の違反も件数に乗る" {
            val violation = MinimalViolation(
                path = "app/src/Foo.kt",
                label = "TodoRule",
                kind = ViolationKind.Constraint,
            )

            listOf(violation).report().lines().first() shouldBe
                "Katachi check failed: 1 violation (Constraint: 1)"
        }
    }
})
