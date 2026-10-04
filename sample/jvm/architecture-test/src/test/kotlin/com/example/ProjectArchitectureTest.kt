package com.example

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

/**
 * The adoption step itself: one test that checks the project against [projectArchitecture].
 *
 * **This is the only test a project adopting katachi writes.** Every violation of the whole
 * repository arrives in a single failure message, so there is nothing to gain from splitting
 * it up.
 *
 * `FileConstraintCheck()` is passed explicitly because katachi never runs a constraint nobody
 * asked for: `assert()` alone would leave the `konsist { }` and `ktFileContent { }` constraints
 * of the roles (`roles/ServiceRole.kt` holds one of each) reported as
 * `[UncheckedFileConstraint] reason=NotEvaluated` instead of evaluated.
 *
 * Everything else in this module - the `*Spec` files - is katachi's own integration test
 * and has no place in a project that merely uses katachi.
 */
@OptIn(ExperimentalKatachiApi::class)
class ProjectArchitectureTest {
    @Test
    fun `the project structure matches the definition`() {
        projectArchitecture.assert(FileConstraintCheck())
    }
}
