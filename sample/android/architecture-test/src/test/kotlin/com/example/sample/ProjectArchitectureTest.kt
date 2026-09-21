package com.example.sample

import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

/**
 * The adoption step itself: one test that checks the project against [projectArchitecture].
 *
 * **This is the only test a project adopting katachi writes.** Every violation of the whole
 * repository arrives in a single failure message, so there is nothing to gain from splitting
 * it up. The other files in this directory end in `Spec` and are katachi's own integration
 * tests — a project that merely uses katachi does not need them.
 */
class ProjectArchitectureTest {
    @Test
    fun `プロジェクトの構成が定義どおりになっている`() {
        projectArchitecture.assert()
    }
}
