@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konture

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UncheckedFileConstraintReason
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.konture.KatachiKontureNoExpectationException
import me.tbsten.katachi.konture.KatachiKontureNoKotlinFilesException

/**
 * What a `konture { }` constraint produces once it has been through the whole machine: the
 * walk, the check, the violation and the report a reader actually sees.
 *
 * [KontureScopeSpec] is about what the block collects; this is about what comes out the other
 * end, because that text is the only thing a failing test shows.
 */
class KontureFileConstraintSpec : FreeSpec({
    "落ちた制約が 1 件のブロックになる" {
        val violations = kontureRun(
            "src/HomeViewModel.kt" to COMPOSABLE_VIEW_MODEL_KT,
            "src/InternalThing.kt" to INTERNAL_THING_KT,
        ) {
            classes { should() notDependOnPackages "androidx.compose.." }
        }

        violations.filterIsInstance<UnsatisfiedFileConstraint>().single().path shouldBe
            "src/HomeViewModel.kt"

        val report = violations.report()
        report shouldContain "Katachi check failed: 1 violation (FileConstraint: 1)"
        // Without a root to resolve against, report() prints the path as the violation carries it.
        report shouldContain "[UnsatisfiedFileConstraint] src/HomeViewModel.kt"
        // With one, the first line is a file:// URI a reader -- or an agent -- can open as written.
        violations.report(projectRoot = FsPath.of("/repo")) shouldContain
            "[UnsatisfiedFileConstraint] file:///repo/src/HomeViewModel.kt"
        report shouldContain "Role: domain.UseCase / FileConstraint: \"規約\""
        report shouldContain "Declaration: HomeViewModel (line 5)"
        report shouldContain "(layout of src)"
        // A constraint block never offers a way to fix itself: what would satisfy an arbitrary
        // Konture rule is not something katachi can know.
        report.contains("How to fix:") shouldBe false
        // Konture's own prose about the violation stays in Konture.
        report.contains("androidx.compose") shouldBe false
    }

    "files の違反には Declaration 行が無い" {
        val violations = kontureRun(
            "src/PublicThing.kt" to PUBLIC_THING_KT,
            "src/Helper.kt" to HELPER_KT,
        ) {
            files { should() nameEndsWith "Thing.kt" }
        }

        val report = violations.report()
        report shouldContain "[UnsatisfiedFileConstraint] src/Helper.kt"
        report.contains("Declaration:") shouldBe false
    }

    "何も期待しないブロックは緑にならない" {
        // A block that declares no rule evaluates nothing, which in a report is indistinguishable
        // from a rule everything satisfies. This backend can count its own rules, so it refuses
        // instead of answering.
        val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
        }

        val unchecked = violations.filterIsInstance<UncheckedFileConstraint>().single()
        unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
        unchecked.cause.shouldBeInstanceOf<KatachiKontureNoExpectationException>()
            .role shouldBe "domain.UseCase"

        val report = violations.report()
        report shouldContain "[UncheckedFileConstraint] src"
        report shouldContain "Katachi failed while evaluating this constraint"
        report shouldContain "declares no rule"
        report.lines().last() shouldBe "1 constraint could not be evaluated."
    }

    "Konture が読めるファイルが 1 つも無いときも緑にならない" {
        val violations = kontureRun(
            "src/Task.kts" to "val task = 1\n",
            declarations = listOf("Task.kts"),
        ) {
            files { should() satisfy { false } }
        }

        val unchecked = violations.filterIsInstance<UncheckedFileConstraint>().single()
        unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
        unchecked.cause.shouldBeInstanceOf<KatachiKontureNoKotlinFilesException>().given shouldBe 1
    }

    "制約を評価しなければ NotEvaluated として残る" {
        // The other half of the explicit wiring, seen from this module: `konture { }` written
        // and `FileConstraintCheck()` left out of `assert(...)` is reported rather than passing.
        val violations = kontureRunWithoutFileConstraintCheck("src/PublicThing.kt" to PUBLIC_THING_KT) {
            classes { should().beInternal() }
        }

        val unchecked = violations.filterIsInstance<UncheckedFileConstraint>().single()
        unchecked.reason shouldBe UncheckedFileConstraintReason.NotEvaluated
        unchecked.cause shouldBe null

        val report = violations.report()
        report shouldContain "Nothing evaluated this constraint, so nothing is known about it."
        report shouldContain "Pass FileConstraintCheck() to assert()"
    }

    "同じ JVM で 2 回走らせても同じ答えになる" {
        // Konture keeps a content-hashed parse cache for the life of the JVM, so the second run
        // over the same files is served from it. The answer has to be the same answer.
        fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
            val first = kontureRunIn(root) { classes { should().beInternal() } }
            val second = kontureRunIn(root) { classes { should().beInternal() } }

            first.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}:${it.line}" } shouldBe
                listOf("src/PublicThing.kt:PublicThing:3")
            second.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}:${it.line}" } shouldBe
                listOf("src/PublicThing.kt:PublicThing:3")
        }
    }
})
