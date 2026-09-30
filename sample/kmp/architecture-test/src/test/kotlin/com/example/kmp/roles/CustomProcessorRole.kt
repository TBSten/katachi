package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the processors this sample adds on top of the check katachi ships. */
fun DeclarationContainerScope.customProcessor() = "CustomProcessor" {
    title = "Custom processor"
    summary = "Processors a user adds beside the check katachi ships, kept in the processor package of :architecture-test"
    description = """
        The processors of this sample: classes implementing `ArchitectureProcessorNoArg` that
        read the architecture and return a value or write files. They demonstrate that a user
        of katachi can add a processor of their own without touching katachi itself.

        Files named `*Processor.kt` in the `processor` package are this role. The metadata key
        the processors read is the ProcessorMetadata role, and their tests are ProcessorSpec.
    """.trimIndent()
    example("PlatformOwnedFilesProcessor.kt", "Lists the files of roles tagged with an owner")
    example("RoleSummaryReportProcessor.kt", "Writes one short page per role")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/kmp" / "processor" / "*Processor".ktFile()
        }
    }
}
