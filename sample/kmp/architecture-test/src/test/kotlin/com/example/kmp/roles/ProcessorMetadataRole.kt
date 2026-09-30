package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the file that brings the sample's own `owner` metadata into the DSL. */
fun DeclarationContainerScope.processorMetadata() = "ProcessorMetadata" {
    title = "Processor metadata"
    summary = "Owner.kt, the metadata key and property a custom processor reads, kept in the processor package"
    description = """
        The custom metadata of this sample: a `MetadataKey` and the property that writes it
        (`owner`), which a custom processor then reads. `owner` is not a word katachi ships, so
        it lives with the processor package rather than in the definition.

        Only `Owner.kt` is declared, by name. A new key would get its own role, or be added
        here once there is a second one to justify a pattern.
    """.trimIndent()
    example("Owner.kt", "The metadata key `owner` and the property that writes it")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/kmp" / "processor" / "Owner".ktFile()
        }
    }
}
