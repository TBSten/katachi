package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the tests of this sample's processors and of katachi's own sentinel, which a project merely using katachi does not need. */
fun DeclarationContainerScope.integrationSpec() = "IntegrationSpec" {
    title = "Integration spec"
    summary = "The tests that call the processors through the API, and katachi's own sentinel run against this real project"
    description = """
        `CustomProcessorSpec` calls the three processors the way a user calls them, with
        `projectArchitecture.process(...)`, and asserts what they return. `LayoutSnapshotSpec` is
        katachi's own sentinel: it checks that the layout snapshot is up to date. Both are matched
        by the `*Spec` name.

        A project that merely uses katachi does not write the second one. The first is the part of
        this sample a reader copies when testing a processor of their own.
    """.trimIndent()
    forbiddenContents = """
        - The test a project adopting katachi writes. That is `ProjectArchitectureTest`
        - The processors themselves. `processors/` belongs to the "Processor" role
    """.trimIndent()
    example("CustomProcessorSpec.kt", "The processors called the way a user calls them")
    layout {
        "architecture-test" / testSourceSet / kotlin / "com/example" / "*Spec".ktFile()
    }
}
