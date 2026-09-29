package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the katachi definition itself, which belongs to no layer of the application. */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "Architecture definition"
    summary = "Role definitions written in the katachi DSL, and the tests that assert them"
    description = """
        This is the code that describes the shape of this project. It belongs to no layer of the
        application, so it lives in a dedicated module, `:architecture-test`.

        It is one declaration per file. `ProjectArchitecture.kt` is the entry point,
        `groups/<Name>Group.kt` holds a group and `roles/<Name>Role.kt` holds a role. The section
        definitions that every group and role uses through `by` are collected in `DocumentSections.kt`,
        which, like `ProjectArchitecture.kt`, is allowed by name. `ProjectArchitectureTest`, which
        checks the project against the definition, `CustomProcessorSpec`, which calls the three
        processors through the API, and `LayoutSnapshotSpec`, katachi's own sentinel, live in the same
        module and are covered by this role.

        `layout { }` writes the package as `"com/example"` directly. It does not use `modulePackage`,
        because this module's sources are in `com/example`, not in `com/example/architectureTest`, which
        is what would be derived from the module name.
    """.trimIndent()
    forbiddenContents = """
        - The processors themselves. `processors/` belongs to the "Processor" role, and the split is
          deliberate. A definition writes a shape and a processor reads that shape to produce
          something, so they face opposite directions. Keeping them apart also gives them separate
          rows in the output of `RoleFileCount`
        - Application code. If you create `src/main/kotlin` in `:architecture-test`, its files fail as
          covered by no role
    """.trimIndent()
    example("ProjectArchitecture.kt", "The entry point of the definition")
    example("roles/StoreRole.kt", "The declaration of a single role")
    example("ProjectArchitectureTest.kt", "The test that asserts the definition")
    layout {
        // The price of the recommended setup: `:architecture-test` checks itself, so the
        // definition has to give itself a role like everything else. `processors/` is
        // deliberately not listed here -- the `Processor` role claims it.
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" {
                "ProjectArchitecture".ktFile()
                "DocumentSections".ktFile()
                "ProjectArchitectureTest".ktFile()
                "*Spec".ktFile()
                "groups" / "*".ktFile()
                "roles" / "*".ktFile()
            }
        }
    }
}
