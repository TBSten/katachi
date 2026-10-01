package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the entry point of the katachi definition, which belongs to no layer of the application. */
fun DeclarationContainerScope.architectureDefinitionEntry() = "ArchitectureDefinitionEntry" {
    title = "Architecture definition entry"
    summary = "The one file whose `architecture { }` gathers every group, so the definition can be read from here"
    description = """
        `ProjectArchitecture.kt` holds `projectArchitecture`, the value every test and processor of this
        project reads, and it only calls the group functions. The groups, the roles, the processors and
        the section definitions are other kinds of file with their own roles, so this role is the one
        file and nothing else.

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
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" / "ProjectArchitecture".ktFile()
        }
    }
}
