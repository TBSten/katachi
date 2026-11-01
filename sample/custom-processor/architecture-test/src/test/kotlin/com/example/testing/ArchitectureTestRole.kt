package com.example.testing

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

fun DeclarationContainerScope.architectureTest() = "ArchitectureTest".role {
    title = "Architecture test"
    summary = "The one test that checks the whole project against the definition"
    description = """
        `ProjectArchitectureTest` calls `assert(FileConstraintCheck())` on the definition. Every
        violation of the repository arrives in a single failure message, so there is nothing to
        gain from splitting it up. It is the only test a project adopting katachi writes.
        `FileConstraintCheck()` is what makes the Model role's `konture { }` constraint run;
        without it the constraint is reported as not evaluated.

        The file name is `*Test.kt`. The `*Spec` files next to it have a role of their own.
    """.trimIndent()
    forbiddenContents = """
        - Tests of the application. Those go in the root project's test source set
        - The processors' tests. Those are `*Spec` files, which belong to the integration spec role
    """.trimIndent()
    example("ProjectArchitectureTest.kt", "The test that asserts the definition")
    layout {
        "architecture-test" / testSourceSet / kotlin / "com/example" / "*Test".ktFile()
    }
}
