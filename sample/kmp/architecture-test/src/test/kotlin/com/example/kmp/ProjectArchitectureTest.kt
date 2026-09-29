package com.example.kmp

import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

/**
 * The adoption step itself: one test that checks the project against [projectArchitecture].
 *
 * **This is the only test a project adopting katachi writes.** Every violation of the whole
 * repository arrives in a single failure message, so there is nothing to gain from splitting
 * it up. There is no list of rules to keep in step with the definition either -- the
 * definition *is* the rule.
 *
 * Nothing here mentions a path, a file system or a project root: `assert()` finds the project
 * root by walking up from the working directory (see [ProjectRootSpec]) and reads the tree from
 * there. The other files in this directory end in `Spec` and are katachi's own integration
 * tests -- a project that merely uses katachi does not need them.
 */
class ProjectArchitectureTest {
    @Test
    fun `the project layout matches the definition`() {
        projectArchitecture.assert()
    }
}
