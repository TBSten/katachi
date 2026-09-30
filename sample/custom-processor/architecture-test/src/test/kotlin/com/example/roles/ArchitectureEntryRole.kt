package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the entry point of the katachi definition and the sections its groups and roles share. */
fun DeclarationContainerScope.architectureEntry() = "ArchitectureEntry" {
    title = "Architecture entry"
    summary = "The entry point of the katachi definition, and the section definitions shared by its groups and roles"
    description = """
        `ProjectArchitecture.kt` holds `projectArchitecture`, the value every test and processor of this
        project reads, and `DocumentSections.kt` holds the section definitions that every group and role
        uses through `by`. Both are allowed by name, so adding a third file here is a decision, not an
        accident.

        `layout { }` writes the package as `"com/example"` directly. It does not use `modulePackage`,
        because this module's sources are in `com/example`, not in `com/example/architectureTest`, which
        is what would be derived from the module name.
    """.trimIndent()
    forbiddenContents = """
        - A group or a role. Those belong to the group definition and role definition roles, one
          declaration per file
        - The processors. `processors/` belongs to the "Processor" role
    """.trimIndent()
    example("ProjectArchitecture.kt", "The entry point of the definition")
    example("DocumentSections.kt", "The sections every group and role shares")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" {
                "ProjectArchitecture".ktFile()
                "DocumentSections".ktFile()
            }
        }
    }
}
