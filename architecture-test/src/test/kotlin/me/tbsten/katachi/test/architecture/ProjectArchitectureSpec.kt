package me.tbsten.katachi.test.architecture

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.ConstraintCheck
import me.tbsten.katachi.check.assert
import me.tbsten.katachi.check.validate

/**
 * Asserts that this repository is shaped the way [projectArchitecture] says it is.
 *
 * The whole of adopting katachi: one call, and the report it throws names every file nobody
 * declared, every declared file that is missing, and every constraint that was not satisfied.
 *
 * `ConstraintCheck()` is not optional here. The six roles of the `library` group declare three
 * `konsist { }` each and `backend/KonsistBackend` declares two, and a bare `assert()` evaluates
 * no constraints at all — it would report twenty `[UncheckedConstraint] reason=NotEvaluated`
 * blocks instead of checking the import directions. A project that declares no constraint can
 * leave it out; this one cannot.
 */
@OptIn(ExperimentalKatachiApi::class, InternalKatachiApi::class)
class ProjectArchitectureSpec : FreeSpec({
    "リポジトリ全体が projectArchitecture の宣言どおりである" {
        projectArchitecture.assert(ConstraintCheck())
    }

    "警告も含めて違反が1件も出ない" {
        // `assert()` above throws only on `Severity.Error`; a warning is printed to stderr and
        // the test still passes, and a passing test's stderr never reaches the Gradle console.
        // So a `[MissingDescription]` or `[AmbiguousLayout]` introduced by an edit to a
        // `layout { }` would go unnoticed here. `validate()` answers with every violation of
        // the run, warnings included, which is the only mechanical net for them in this module.
        projectArchitecture.validate(ConstraintCheck()) shouldBe emptyList()
    }
})
