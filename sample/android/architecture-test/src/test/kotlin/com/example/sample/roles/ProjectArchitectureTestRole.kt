package com.example.sample.roles

import com.example.sample.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the one test a user writes: the check that runs the definition. */
fun DeclarationContainerScope.projectArchitectureTest() = "ProjectArchitectureTest" {
    title = "Architecture test"
    summary = "The single JUnit test that runs the definition, kept in src/test/kotlin of :architecture-test"
    description = """
        The test that checks the whole project through the definition written here: a single
        JUnit test that calls `projectArchitecture.assert()`. This is the only test a project
        adopting katachi writes. The tests in which a feature module checks its own ViewModel
        are a different role (feature test) and live in each feature's `src/test`.

        Files named `*Test.kt` directly under the package (outside `groups/` and `roles/`) are
        this role. That tells them apart from the architecture definition roles in the same
        module: putting a test in `roles/` gives an `[UnexpectedFile]`, and conversely a
        definition helper cannot be placed next to the test.
    """.trimIndent()
    forbiddenContents = """
        Tools used from tests. Stand-in implementations handed to other modules' tests belong to
        the fake role of `:testing`, and cannot be exposed from `src/test`.
    """.trimIndent()
    example("ProjectArchitectureTest", "The only test a user writes")
    layout {
        // Only the top level of the package: `groups/` and `roles/` hold declarations,
        // never tests, which is what the definition roles say on their side.
        "architecture-test" {
            testSourceSet / kotlin / "com/example/sample" {
                "*Test".ktFile()
            }
        }
    }
}
