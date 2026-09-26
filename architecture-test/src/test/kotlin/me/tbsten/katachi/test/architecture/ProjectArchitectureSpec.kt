package me.tbsten.katachi.test.architecture

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.assert
import me.tbsten.katachi.check.internal.validate

/**
 * Asserts that this repository is shaped the way [projectArchitecture] says it is.
 *
 * The whole of adopting katachi: one call, and the report it throws names every file nobody
 * declared, every declared file that is missing, and every constraint that was not satisfied.
 *
 * `FileConstraintCheck()` is not optional here. The roles of the `library` group declare five
 * `konsist { }` each, `KonsistBackend` declares four and `DokkaPlugin` declares one, and a bare
 * `assert()` evaluates no constraints at all — it would report every one of them as an
 * `[UncheckedFileConstraint] reason=NotEvaluated` block instead of checking the import directions. A
 * project that declares no constraint can leave it out; this one cannot.
 */
@OptIn(ExperimentalKatachiApi::class, InternalKatachiApi::class)
class ProjectArchitectureSpec : FreeSpec({
    "リポジトリ全体が projectArchitecture の宣言どおりである" {
        projectArchitecture.assert(FileConstraintCheck())
    }

    "警告も含めて違反が1件も出ない" {
        // `assert()` above throws only on `Severity.Error`; a warning is printed to stderr and
        // the test still passes, and a passing test's stderr never reaches the Gradle console.
        // So a `[MissingDescription]` or `[AmbiguousLayout]` introduced by an edit to a
        // `layout { }` would go unnoticed here. `validate()` answers with every violation of
        // the run, warnings included, which is the only mechanical net for them in this module.
        projectArchitecture.validate(FileConstraintCheck()) shouldBe emptyList()
    }
})
