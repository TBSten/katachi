package me.tbsten.katachi.test.architecture

import io.kotest.core.spec.style.FreeSpec
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.ConstraintCheck
import me.tbsten.katachi.check.assert

/**
 * Asserts that this repository is shaped the way [projectArchitecture] says it is.
 *
 * The whole of adopting katachi: one call, and the report it throws names every file nobody
 * declared, every declared file that is missing, and every constraint that was not satisfied.
 *
 * `ConstraintCheck()` is not optional here. The seven roles of the `library` group each declare a
 * `konsist { }`, and a bare `assert()` evaluates no constraints at all — it would report seven
 * `[UncheckedConstraint] reason=NotEvaluated` blocks instead of checking the import
 * directions. A project that declares no constraint can leave it out; this one cannot.
 */
@OptIn(ExperimentalKatachiApi::class)
class ProjectArchitectureSpec : FreeSpec({
    "リポジトリ全体が projectArchitecture の宣言どおりである" {
        projectArchitecture.assert(ConstraintCheck())
    }
})
