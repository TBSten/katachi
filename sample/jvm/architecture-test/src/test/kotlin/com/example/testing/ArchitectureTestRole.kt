package com.example.testing

import com.example.data.repository
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

        The file name is `*Test.kt`. The `*Spec` files next to it are katachi's own integration
        tests, which have a role of their own.

        `FileConstraintCheck()` is passed explicitly because katachi never runs a constraint
        nobody asked for: with a bare `assert()`, the `ktFileContent { }` constraints of the
        roles are reported as `[UncheckedFileConstraint]` (`NotEvaluated`) instead of evaluated.
        When there are more violations than the report shows, run
        `./gradlew :architecture-test:test -Dkatachi.maxViolations=all`. katachi's Gradle plugin
        hands the property on to the test JVM, so the test does not change.
    """.trimIndent()
    forbiddenContents = """
        - Tests of the application. Those go in the root project's test source set (the Test role)
    """.trimIndent()
    example("ProjectArchitectureTest.kt", "The test that asserts the definition")
    layout {
        "architecture-test" / testSourceSet / kotlin / "com/example" / "*Test".ktFile()
    }
}
