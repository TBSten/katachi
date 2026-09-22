@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.report
import me.tbsten.katachi.konsist.KatachiKonsistNoExpectationException
import me.tbsten.katachi.konsist.KatachiKonsistNoKotlinFilesException
import me.tbsten.katachi.scan.UncheckedConstraint
import me.tbsten.katachi.scan.UncheckedConstraintReason
import me.tbsten.katachi.scan.UnsatisfiedConstraint

/**
 * What a `konsist { }` constraint produces once it has been through the whole machine: the
 * walk, the check, the violation and the report a reader actually sees.
 *
 * [KonsistScopeSpec] is about what the block collects; this is about what comes out the other
 * end, because that text is the only thing a failing test shows.
 */
class KonsistConstraintSpec : FreeSpec({
    "落ちた制約が 1 件のブロックになる" {
        val violations = konsistRun(
            "src/PublicThing.kt" to PUBLIC_THING_KT,
            "src/InternalThing.kt" to INTERNAL_THING_KT,
        ) {
            classes().must { it.hasInternalModifier }
        }

        violations.filterIsInstance<UnsatisfiedConstraint>().single().path shouldBe
            "src/PublicThing.kt"

        val report = violations.report()
        report shouldContain "Katachi check failed: 1 violation (Constraint: 1)"
        // The first line is a path a reader -- or an agent -- can open as written.
        report shouldContain "[UnsatisfiedConstraint] src/PublicThing.kt"
        report shouldContain "Role: domain/UseCase / Constraint: \"規約\""
        report shouldContain "Declaration: PublicThing (line 3)"
        report shouldContain "(layout of src)"
        // A constraint block never offers a way to fix itself: what would satisfy an arbitrary
        // Kotlin predicate is not something katachi can know.
        report.contains("How to fix:") shouldBe false
    }

    "何も期待しないブロックは緑にならない" {
        // A block that queries and never ends the query rejects nothing, which in a report is
        // indistinguishable from a rule everything satisfies. This backend can count its own
        // expectations, so it refuses instead of answering.
        val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
            classes()
        }

        val unchecked = violations.filterIsInstance<UncheckedConstraint>().single()
        unchecked.reason shouldBe UncheckedConstraintReason.Failed
        unchecked.cause.shouldBeInstanceOf<KatachiKonsistNoExpectationException>()
            .role shouldBe "domain/UseCase"

        val report = violations.report()
        report shouldContain "[UncheckedConstraint] src"
        report shouldContain "Katachi failed while evaluating this constraint"
        report shouldContain "never called must, mustNot or mustBeEmpty"
        report.lines().last() shouldBe "1 constraint could not be evaluated."
    }

    "Konsist が読めるファイルが 1 つも無いときも緑にならない" {
        val violations = konsistRun(
            "src/Task.kts" to "val task = 1\n",
            declarations = listOf("Task.kts"),
        ) {
            classes().mustBeEmpty()
        }

        val unchecked = violations.filterIsInstance<UncheckedConstraint>().single()
        unchecked.reason shouldBe UncheckedConstraintReason.Failed
        unchecked.cause.shouldBeInstanceOf<KatachiKonsistNoKotlinFilesException>().given shouldBe 1
    }

    "制約を評価しなければ NotEvaluated として残る" {
        // The other half of the explicit wiring, seen from this module: `konsist { }` written
        // and `ConstraintCheck()` left out of `assert(...)` is reported rather than passing.
        val violations = konsistRunWithoutConstraintCheck("src/PublicThing.kt" to PUBLIC_THING_KT) {
            classes().must { it.hasInternalModifier }
        }

        val unchecked = violations.filterIsInstance<UncheckedConstraint>().single()
        unchecked.reason shouldBe UncheckedConstraintReason.NotEvaluated
        unchecked.cause shouldBe null

        val report = violations.report()
        report shouldContain "Nothing evaluated this constraint, so nothing is known about it."
        report shouldContain "Pass ConstraintCheck() to assert()"
    }
})
