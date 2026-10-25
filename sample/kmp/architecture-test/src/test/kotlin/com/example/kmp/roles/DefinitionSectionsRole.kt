package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the section headings every group and role writes through. */
fun DeclarationContainerScope.definitionSections() = "DefinitionSections".role {
    title = "Definition sections"
    summary = "DocumentSections.kt, the section headings every group and role writes through"
    description = """
        `DocumentSections.kt` gathers just the section definitions (`allowedContents`,
        `forbiddenContents` and the like) that every group and role uses with `by`. It is the
        one file beside `ProjectArchitecture.kt` that every group and role reads, and it is named
        exactly for the same reason: anything else that slips in next to the definition becomes
        an `[UnexpectedFile]`. The other vocabulary a few of them share, the `owner` key, belongs
        to the processor that reads it and sits in `processors/Owner.kt` (ProcessorMetadata).
    """.trimIndent()
    example("DocumentSections.kt", "The section headings shared by every group and role")
    layout {
        "architecture-test" {
            testSourceSet / kotlin / "com/example/kmp" / "DocumentSections".ktFile()
        }
    }
}
