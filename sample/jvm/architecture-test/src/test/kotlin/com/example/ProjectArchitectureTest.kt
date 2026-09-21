package com.example

import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

/**
 * Step 4 of adopting katachi, and the only test a user writes.
 *
 * One call checks the whole project: every violation lands in a single failure message, so
 * reading it costs one test run no matter how many things are off. Splitting this into a
 * test per role would cost one run per violation and say nothing more.
 *
 * Everything else in this module - the `*Spec` files - is katachi's own integration test
 * and has no place in a project that merely uses katachi.
 */
class ProjectArchitectureTest {
    @Test
    fun `構成が allow list に従っている`() {
        projectArchitecture.assert()
    }
}
