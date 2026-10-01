package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the one test a user writes: the check that runs the definition. */
fun DeclarationContainerScope.projectArchitectureTest() = "ProjectArchitectureTest" {
    title = "Architecture test"
    summary = "The single JUnit test that runs the definition, kept in src/test/kotlin of :architecture-test"
    description = """
        The test that checks the whole project through the definition: a single JUnit test that
        calls `projectArchitecture.assert()`. This is the only test a project adopting katachi
        writes. The definition it runs is described by the DefinitionEntry, GroupDefinition and
        RoleDefinition roles, and kept apart from this file because the definition's job is to
        describe the project.

        Files named `*Test.kt` directly under the package (outside `groups/`, `roles/` and
        `processors/`) are this role. That tells them apart from the roles of the definition in
        the same module.
    """.trimIndent()
    forbiddenContents = """
        - Tests of katachi itself (`*Spec.kt`). They are the ProjectArchitectureSpec role
    """.trimIndent()
    example("ProjectArchitectureTest", "The only test a user writes")
    layout {
        "architecture-test" {
            testSourceSet / kotlin / "com/example/kmp" / "*Test".ktFile()
        }
    }
}
