package com.example

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.ConstraintCheck
import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

/**
 * Step 4 of adopting katachi, and the only test a user writes.
 *
 * One call checks the whole project: every violation lands in a single failure message, so
 * reading it costs one test run no matter how many things are off. Splitting this into a
 * test per role would cost one run per violation and say nothing more.
 *
 * `ConstraintCheck()` is passed explicitly because katachi never runs a `konsist { }` block
 * nobody asked for: `assert()` alone would leave this project's one constraint (in
 * `DomainRoles.kt`'s `Service` role) reported as `[UncheckedConstraint] reason=NotEvaluated`
 * instead of evaluated.
 *
 * Everything else in this module - the `*Spec` files - is katachi's own integration test
 * and has no place in a project that merely uses katachi.
 */
@OptIn(ExperimentalKatachiApi::class)
class ProjectArchitectureTest {
    @Test
    fun `構成が allow list に従っている`() {
        projectArchitecture.assert(ConstraintCheck())
    }
}
