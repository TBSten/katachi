package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.report
import me.tbsten.katachi.scan.Severity
import me.tbsten.katachi.scan.Violation
import me.tbsten.katachi.scan.ViolationKind

/**
 * A [Violation] with [Severity.Error], the way a third-party check would define one: no access
 * to katachi's internal constructors, only the public interface. Stands in for one of katachi's
 * own error violations wherever a case only needs *some* error, not what kind of error it is.
 */
private class TestError(
    override val path: String,
    override val label: String = "TestError",
    override val kind: ViolationKind = ViolationKind.Unexpected,
) : Violation {
    override val severity: Severity get() = Severity.Error
}

/**
 * A [Violation] with [Severity.Warning], the same way. Step 5-1 wires the Warning path through
 * `report()` and `assert()` before either of katachi's own Warning kinds has a detector
 * ([ViolationKind.Ambiguous] / [ViolationKind.Unexplained] land in step 5-2 / 5-3), so this
 * stands in for both until then.
 */
private class TestWarning(
    override val path: String,
    override val label: String = "TestWarning",
    override val kind: ViolationKind = ViolationKind.Ambiguous,
) : Violation {
    override val severity: Severity get() = Severity.Warning
}

/**
 * `report()`'s contract and the Warning section it adds: how a run with nothing, only
 * warnings, or both errors and warnings renders, and how the truncation budget splits between
 * the two sections.
 */
class WarningReportSpec : FreeSpec({
    "report() の契約" - {
        "違反が1件も無ければ空文字列" {
            emptyList<Violation>().report() shouldBe ""
        }

        "Warning だけなら Katachi check failed 行が無く Warning セクションだけになる" {
            listOf(TestWarning(path = "docs/a.md")).report() shouldBe
                """
                Katachi check found 1 warning. Warnings never fail the check.

                [TestWarning] docs/a.md
                """.trimIndent()
        }

        "Warning が複数なら見出しが複数形になる" {
            listOf(TestWarning(path = "docs/a.md"), TestWarning(path = "docs/b.md")).report() shouldBe
                """
                Katachi check found 2 warnings. Warnings never fail the check.

                [TestWarning] docs/a.md

                [TestWarning] docs/b.md
                """.trimIndent()
        }

        "Error と Warning が両方あるとき失敗メッセージの末尾に Warning セクションが来る" {
            listOf(TestError(path = "app/Foo.kt"), TestWarning(path = "docs/a.md")).report() shouldBe
                """
                Katachi check failed: 1 violation (Unexpected: 1), 1 warning

                [TestError] app/Foo.kt

                Katachi check found 1 warning. Warnings never fail the check.

                [TestWarning] docs/a.md
                """.trimIndent()
        }
    }

    "サマリ行" - {
        "violations の件数と種別内訳には Warning が入らない" {
            val violations = listOf(
                TestError(path = "a", kind = ViolationKind.Unexpected),
                TestError(path = "b", kind = ViolationKind.Missing),
            ) + List(3) { TestWarning(path = "w$it") }

            violations.report().lines().first() shouldBe
                "Katachi check failed: 2 violations (Unexpected: 1, Missing: 1), 3 warnings"
        }

        "Warning が無ければ従来どおり件数だけで warnings は付かない" {
            listOf(TestError(path = "a")).report().lines().first() shouldBe
                "Katachi check failed: 1 violation (Unexpected: 1)"
        }
    }

    "打ち切りの予算" - {
        "Error がいくら多くても Warning は必ず1ブロック出る" {
            val violations = (1..12).map { TestError(path = "e$it") } +
                (1..3).map { TestWarning(path = "w$it") }

            val report = violations.report(maxViolations = 10)
            val lines = report.lines()

            // budget=10, warningBudget = min(3, max(1, 10/4=2)) = 2, errorBudget = 8.
            lines.count { it.startsWith("[TestError]") } shouldBe 8
            lines.count { it.startsWith("[TestWarning]") } shouldBe 2
            // The combined total never exceeds maxViolations.
            lines.count { it.startsWith("[") } shouldBe 10
        }

        "Warning 側の打ち切り行が出る" {
            val violations = (1..12).map { TestError(path = "e$it") } +
                (1..3).map { TestWarning(path = "w$it") }

            val lines = violations.report(maxViolations = 10).lines()

            lines shouldContain "Showing first 8 (4 more)"
            lines shouldContain "Showing first 2 warnings (1 more)"
        }

        "Warning だけのときはその severity が予算を丸ごと使う" {
            val violations = (1..12).map { TestWarning(path = "w$it") }

            val report = violations.report(maxViolations = 10)

            report.lines().count { it.startsWith("[TestWarning]") } shouldBe 10
            report.lines().last() shouldBe "Showing first 10 warnings (2 more)"
        }

        "maxViolations = 0 だと Warning も1ブロックも出ない" {
            val violations = listOf(TestError(path = "e"), TestWarning(path = "w"))

            val report = violations.report(maxViolations = 0)

            report.lines().count { it.startsWith("[") } shouldBe 0
        }
    }
})
