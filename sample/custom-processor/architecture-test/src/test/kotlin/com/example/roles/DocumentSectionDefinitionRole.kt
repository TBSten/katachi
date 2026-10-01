package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the headings this definition adds to the generated documentation. */
fun DeclarationContainerScope.documentSectionDefinition() = "DocumentSectionDefinition" {
    title = "Document section definition"
    summary = "The headings this project declares for itself, so roles and groups can write under them"
    description = """
        `DocumentSections.kt` declares the headings that appear in the generated pages besides
        katachi's own ones ("Allowed contents", "Forbidden contents"), and the property each
        role or group writes them with. It is a vocabulary the role files share and no role
        owns, so it is a file of its own next to the entry.
    """.trimIndent()
    forbiddenContents = """
        - A group or a role. Those go in `groups/` and `roles/`
    """.trimIndent()
    example("DocumentSections.kt", "The two headings and their properties")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" / "DocumentSections".ktFile()
        }
    }
}
