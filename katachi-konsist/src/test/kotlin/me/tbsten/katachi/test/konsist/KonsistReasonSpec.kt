@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.reportForTest
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason

/**
 * `must(reason, howToFix) { }` and its two siblings: a `konsist { }` rule that says why it exists
 * and how to satisfy it, printed under every element it rejects.
 *
 * The overloads without a reason are pinned by the rest of this module's specs, which keep
 * passing unchanged -- that is the claim that a rule written before this existed reads the same.
 */

/** The rejections of a run, which is what each case reads the details off. */
private fun List<Violation>.unsatisfied(): List<UnsatisfiedFileConstraint> = filterIsInstance<UnsatisfiedFileConstraint>()

/** Asserts that the report prints [lines] one after another, exactly as given. */
private fun List<Violation>.shouldReportLines(vararg lines: String) {
    reportForTest() shouldContain lines.joinToString("\n")
}

class KonsistReasonSpec : FreeSpec({
    "must(reason, howToFix)" - {
        "Reason: の行と How to fix: を出す" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().must("this layer is internal", listOf("Make the class internal")) { it.hasInternalModifier }
            }

            val violation = violations.unsatisfied().single()
            violation.reasons shouldBe listOf(FileConstraintReason("Reason", "this layer is internal"))
            violation.howToFix shouldBe listOf(FileConstraintFix("Make the class internal"))
            violations.shouldReportLines("  Declaration: PublicThing (line 3)", "  Reason: this layer is internal")
            violations.shouldReportLines("", "  How to fix:", "    - Make the class internal")
        }

        "直し方を書かなければ How to fix: は出ない" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().must("this layer is internal") { it.hasInternalModifier }
            }

            violations.unsatisfied().single().howToFix.shouldBeEmpty()
            violations.reportForTest() shouldNotContain "How to fix:"
            violations.shouldReportLines("  Reason: this layer is internal")
        }

        "理由を付けた must も、何かを求めたことに数える" {
            // Nothing is rejected and nothing is refused: the block asked, and the answer is "none".
            val violations = konsistRun("src/InternalThing.kt" to INTERNAL_THING_KT) {
                classes().must("this layer is internal") { it.hasInternalModifier }
            }

            violations.shouldBeEmpty()
        }
    }

    "mustNot(reason, howToFix)" - {
        "当たった要素に Reason: と How to fix: が付く" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().mustNot("public classes leak the layer", listOf("Make it internal", "Move it to :api")) {
                    it.hasPublicOrDefaultModifier
                }
            }

            violations.unsatisfied().single().howToFix shouldBe
                listOf(FileConstraintFix("Make it internal"), FileConstraintFix("Move it to :api"))
            violations.shouldReportLines("  Reason: public classes leak the layer")
            violations.shouldReportLines("  How to fix:", "    - Make it internal", "    - Move it to :api")
        }
    }

    "mustBeEmpty(reason, howToFix)" - {
        "残った要素に Reason: と How to fix: が付く" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().filter { it.hasPublicOrDefaultModifier }.mustBeEmpty("no public class here", listOf("Make it internal"))
            }

            violations.unsatisfied().single().reasons shouldBe listOf(FileConstraintReason("Reason", "no public class here"))
            violations.shouldReportLines("  Reason: no public class here")
            violations.shouldReportLines("  How to fix:", "    - Make it internal")
        }
    }

    "理由なしの呼び方" - {
        "must { } は今までどおり Reason: も How to fix: も出さない" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().must { it.hasInternalModifier }
            }

            val violation = violations.unsatisfied().single()
            violation.reasons.shouldBeEmpty()
            violation.howToFix.shouldBeEmpty()
            violations.reportForTest() shouldNotContain "Reason:"
            violations.reportForTest() shouldNotContain "How to fix:"
        }

        "同じ宣言を理由なしで 2 回落としても 1 件" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().must { it.hasInternalModifier }
                classes().mustNot { it.hasPublicOrDefaultModifier }
            }

            violations.unsatisfied().size shouldBe 1
        }

        "同じ宣言を違う理由で 2 回落としたら 2 件" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().must("first rule") { it.hasInternalModifier }
                classes().mustNot("second rule") { it.hasPublicOrDefaultModifier }
            }

            violations.unsatisfied().map { it.reasons.single().text } shouldBe listOf("first rule", "second rule")
            violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
        }
    }
})
