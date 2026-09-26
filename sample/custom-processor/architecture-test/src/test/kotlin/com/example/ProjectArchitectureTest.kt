package com.example

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

/**
 * The only test a user writes, and the same one in all four samples.
 *
 * One call checks the whole project: every violation lands in a single failure message, so
 * reading it costs one test run no matter how many things are off.
 *
 * No `FileConstraintCheck()` is passed, because this definition declares no `konsist { }` constraint.
 * `assert()` with nothing to hand it is the shape `sample/android` and `sample/kmp` use too.
 *
 * Everything the three processors do is asserted next door, in `CustomProcessorSpec`.
 */
@OptIn(ExperimentalKatachiApi::class)
class ProjectArchitectureTest {
    @Test
    fun `構成が allow list に従っている`() {
        projectArchitecture.assert()
    }
}
