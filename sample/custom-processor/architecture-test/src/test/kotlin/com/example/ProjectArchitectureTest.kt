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
 * it up. It is a plain JUnit test, the same in all four samples.
 *
 * `FileConstraintCheck()` is passed because katachi never runs a constraint nobody asked for:
 * `assert()` alone would leave this project's one constraint (the `konture { }` of the `Model`
 * role in `roles/ModelRole.kt`) reported as `[UncheckedFileConstraint] reason=NotEvaluated`
 * instead of evaluated. The other three samples pass it for their `ktFileContent { }` and
 * `konsist { }` constraints.
 *
 * Everything the three processors do is asserted next door, in `CustomProcessorSpec`.
 */
@OptIn(ExperimentalKatachiApi::class)
class ProjectArchitectureTest {
    @Test
    fun `the project layout matches the definition`() {
        projectArchitecture.assert(FileConstraintCheck())
    }
}
