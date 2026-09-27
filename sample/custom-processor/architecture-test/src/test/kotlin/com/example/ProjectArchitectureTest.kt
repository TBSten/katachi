package com.example

import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

/**
 * The adoption step itself: one test that checks the project against [projectArchitecture].
 *
 * **This is the only test a project adopting katachi writes.** Every violation of the whole
 * repository arrives in a single failure message, so there is nothing to gain from splitting
 * it up. It is a plain JUnit test, the same in all four samples.
 *
 * No `FileConstraintCheck()` is passed, because this definition declares no `konsist { }`
 * constraint. `sample/android` and `sample/kmp` call `assert()` bare as well; only `sample/jvm`,
 * the one with a `konsist { }` constraint, passes `FileConstraintCheck()`.
 *
 * Everything the three processors do is asserted next door, in `CustomProcessorSpec`.
 */
class ProjectArchitectureTest {
    @Test
    fun `プロジェクトの構成が定義どおりになっている`() {
        projectArchitecture.assert()
    }
}
