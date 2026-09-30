package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist

/** The role of the entry point of the katachi definition, which belongs to no layer of the application. */
fun DeclarationContainerScope.architectureDefinitionEntry() = "ArchitectureDefinitionEntry" {
    title = "Architecture definition entry"
    summary = "The one file whose `architecture { }` gathers every group, so the definition can be read from here"
    description = """
        The starting point of the definition. `ProjectArchitecture.kt` holds the `architecture { }`
        block and the `val` every role file shares, and it only calls the group functions. The
        groups, the roles and the processors are other kinds of file with their own roles, so
        this role is the one file and nothing else.

        The definition lives in a dedicated module, `:architecture-test`, because it belongs to no
        layer of the application. The application is the root project (`:`), so moving the
        definition out keeps the application's main source set at one.

        The one constraint keeps the entry a table of contents: it declares no group or role
        itself (an extension function on `DeclarationContainerScope`), because those are
        declared in `groups/` and `roles/`.
    """.trimIndent()
    forbiddenContents = """
        - A group or a role declared in this file. Those go in `groups/` and `roles/`
        - Application code. Creating `src/main/kotlin` in `:architecture-test` fails as files that
          no role covers
    """.trimIndent()
    example("ProjectArchitecture.kt", "The entry point of the definition")
    layout {
        // The price of the recommended setup: `:architecture-test` checks itself, so the
        // definition has to give itself a role like everything else. The module path
        // resolves to `architecture-test/`, which is where the files actually are.
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" {
                "Must not declare a group or a role in the entry file".konsist {
                    functions().mustNot { it.receiverType?.name == "DeclarationContainerScope" }
                }
                "ProjectArchitecture".ktFile()
            }
        }
    }
}
