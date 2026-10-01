@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

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
        "architecture-test" {
            testSourceSet / kotlin / "com/example/sample" {
                "DocumentSections".ktFile()
            }
        }
    }
}
