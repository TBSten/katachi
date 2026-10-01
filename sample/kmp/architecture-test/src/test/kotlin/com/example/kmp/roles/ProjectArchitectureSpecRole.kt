package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the tests that verify katachi itself, which a user does not write. */
fun DeclarationContainerScope.projectArchitectureSpec() = "ProjectArchitectureSpec" {
    title = "Architecture spec"
    summary = "Tests that verify the definition and katachi itself, kept in src/test/kotlin of :architecture-test"
    description = """
        The tests that guard the definition and katachi's own behavior. A project adopting
        katachi does not write these; they are here because this sample is also katachi's
        integration test. `ProjectArchitectureSpec` checks the assembled definition,
        `LayoutSnapshotSpec` compares the flattened layout with the snapshot,
        `OmittedRoleSelfCheckSpec` pins what katachi reports for a definition with a group left
        out, and `ProjectRootSpec` watches the result of the project root lookup.

        Files named `*Spec.kt` directly under the package (outside `groups/`, `roles/` and
        `processors/`) are this role. Putting one in `roles/` gives an `[UnexpectedFile]`. The
        specs of the custom processors are the ProcessorSpec role.
    """.trimIndent()
    forbiddenContents = """
        - The one test a user writes (`*Test.kt`). It is the ProjectArchitectureTest role
    """.trimIndent()
    example("ProjectArchitectureSpec", "A test that verifies this definition itself")
    layout {
        "architecture-test" {
            testSourceSet / kotlin / "com/example/kmp" / "*Spec".ktFile()
        }
    }
}
