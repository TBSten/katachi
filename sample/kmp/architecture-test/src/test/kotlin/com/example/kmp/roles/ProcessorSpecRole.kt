package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the tests of this sample's own custom processors. */
fun DeclarationContainerScope.processorSpec() = "ProcessorSpec" {
    title = "Processor spec"
    summary = "Tests of the custom processors, kept in the processors package of :architecture-test"
    description = """
        The tests that run a custom processor against a small architecture and pin what it
        returns, in `processors/*Spec.kt` beside the processor they test. They are a role of
        their own, not part of ProjectArchitectureSpec, because a processor and its spec travel
        together: someone copying a processor into their own project takes the spec with it.
    """.trimIndent()
    example("PlatformOwnedFilesSpec", "The spec of PlatformOwnedFilesProcessor")
    layout {
        "architecture-test" {
            testSourceSet / kotlin / "com/example/kmp" / "processors" / "*Spec".ktFile()
        }
    }
}
