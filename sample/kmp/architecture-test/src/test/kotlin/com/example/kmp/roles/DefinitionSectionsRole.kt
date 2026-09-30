package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the one shared helper the definition allows. */
fun DeclarationContainerScope.definitionSections() = "DefinitionSections" {
    title = "Definition sections"
    summary = "DocumentSections.kt, the section headings every group and role writes through"
    description = """
        `DocumentSections.kt` gathers just the section definitions (`allowedContents`,
        `forbiddenContents` and the like) that every group and role uses with `by`. It is the
        one shared helper the definition allows, named exactly for the same reason
        `ProjectArchitecture.kt` is: anything else that slips in next to the definition becomes
        an `[UnexpectedFile]`.
    """.trimIndent()
    example("DocumentSections.kt", "The section headings shared by every group and role")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/kmp" / "DocumentSections".ktFile()
        }
    }
}
