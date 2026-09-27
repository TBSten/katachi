package com.example

import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

/**
 * The only test a user writes: one plain JUnit test named `ProjectArchitectureTest`, the same
 * in all four samples.
 *
 * One call checks the whole project: every violation lands in a single failure message, so
 * reading it costs one test run no matter how many things are off.
 *
 * No `FileConstraintCheck()` is passed, because this definition declares no `konsist { }`
 * constraint. `sample/android` and `sample/kmp` call `assert()` bare as well; only `sample/jvm`,
 * the one with a `konsist { }` constraint, passes `FileConstraintCheck()`.
 *
 * Everything the three processors do is asserted next door, in `CustomProcessorSpec`.
 */
class ProjectArchitectureTest {
    @Test
    fun `構成が allow list に従っている`() {
        projectArchitecture.assert()
    }
}
