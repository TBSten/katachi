@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/** The role of the section headings every group and role writes through. */
fun DeclarationContainerScope.definitionSections() = "DefinitionSections".role {
    title = "Definition sections"
    summary = "DocumentSections.kt, the section headings every group and role writes through"
    description = """
        `DocumentSections.kt` gathers just the section definitions (`allowedContents`,
        `forbiddenContents` and the like) that every group and role uses with `by`. It is the
        one file beside `ProjectArchitecture.kt` that every group and role reads, and it is named
        exactly for the same reason: anything else that slips in next to the definition becomes
        an `[UnexpectedFile]`. A helper that only the roles of one group read sits in that
        group's file instead (`featureSources` and `FeatureModule` in `groups/FeatureGroup.kt`,
        `DataDomain` in `groups/DataGroup.kt`).
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
