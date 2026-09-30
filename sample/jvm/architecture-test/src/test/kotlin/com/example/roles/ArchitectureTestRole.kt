package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the one test a project adopting katachi writes. */
fun DeclarationContainerScope.architectureTest() = "ArchitectureTest" {
    title = "Architecture test"
    summary = "The one test that checks the whole project against the definition"
    description = """
        `ProjectArchitectureTest` calls `assert(FileConstraintCheck())` on the definition. Every
        violation of the repository arrives in a single failure message, so there is nothing to
        gain from splitting it up. It is the only test a project adopting katachi writes.

        The file name is `*Test.kt`. The `*Spec` files next to it are katachi's own integration
        tests, which have a role of their own.
    """.trimIndent()
    forbiddenContents = """
        - Tests of the application. Those go in the root project's test source set (the Test role)
    """.trimIndent()
    example("ProjectArchitectureTest.kt", "The test that asserts the definition")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" / "*Test".ktFile()
        }
    }
}
