package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the tests that assert the katachi definition. */
fun DeclarationContainerScope.architectureTest() = "ArchitectureTest" {
    title = "Architecture test"
    summary = "The tests that check the project against the definition and exercise the processors"
    description = """
        `ProjectArchitectureTest` checks the project against the definition. `CustomProcessorSpec`
        calls the three processors through the API, and `LayoutSnapshotSpec`, katachi's own sentinel,
        lives in the same module. The last two are matched by the `*Spec` name.
    """.trimIndent()
    forbiddenContents = "Processors. `processors/` belongs to the \"Processor\" role."
    example("ProjectArchitectureTest.kt", "The test that asserts the definition")
    example("CustomProcessorSpec.kt", "The processors called the way a user calls them")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" {
                "ProjectArchitectureTest".ktFile()
                "*Spec".ktFile()
            }
        }
    }
}
