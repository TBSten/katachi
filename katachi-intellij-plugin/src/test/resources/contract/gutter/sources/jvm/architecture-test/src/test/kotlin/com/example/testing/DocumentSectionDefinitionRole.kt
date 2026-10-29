package com.example.testing

import com.example.forbiddenContents
import com.example.tool.documentation
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the headings this definition adds to the generated documentation. */
fun DeclarationContainerScope.documentSectionDefinition() = "DocumentSectionDefinition".role {
    title = "Document section definition"
    summary = "The headings this project declares for itself, so roles and groups can write under them"
    description = """
        `DocumentSections.kt` declares two headings this project adds to the generated pages
        besides katachi's own ones, "Allowed contents" and "Forbidden contents", and the property
        each role or group writes them with. They are a vocabulary the role files share and no
        role owns, so they are a file of their own next to the entry. A heading only one group
        writes stays in that group's file instead: "Test policy" is declared in
        `api/ApiGroup.kt`.
    """.trimIndent()
    forbiddenContents = """
        - A group or a role. Those go in `<Name>Group.kt` and `<Name>Role.kt`
    """.trimIndent()
    example("DocumentSections.kt", "The two headings and their properties")
    layout {
        "architecture-test" / testSourceSet / kotlin / "com/example" / "DocumentSections".ktFile()
    }
}
