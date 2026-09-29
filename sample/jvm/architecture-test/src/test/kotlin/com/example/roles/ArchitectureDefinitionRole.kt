package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.FileConstraintRange.DirectOnly
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist

/** The role of the katachi definition itself, which belongs to no layer of the application. */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "Architecture definition"
    summary = "The role definitions written in katachi's DSL. It belongs to no layer"
    description = """
        The code that describes the shape of this project. Since it belongs to no layer of the
        application, it lives in a dedicated module, `:architecture-test`. The application is the
        root project (`:`), so moving the definition out keeps the application's main source set
        at one.

        It is one declaration per file. `ProjectArchitecture.kt` is the entry point,
        `groups/<Name>Group.kt` holds a group, `roles/<Name>Role.kt` holds a role, and
        `processors/` holds the processors this sample wrote itself. `ProjectArchitectureTest`,
        which checks that the project matches the definition, and the `*Spec` files, which are
        katachi's own integration tests, are in the same module, so this role covers them too.

        `layout { }` looks at everything under `com/example` with `**`. Splitting into `groups/`
        and `roles/` is a convention for readability, not something `layout { }` enforces (a `.kt`
        in `roles/` that declares nothing still passes).

        Instead, only the reverse convention is checked, with `konsist(scope = DirectOnly)`:
        no group or role declaration (an extension function on `DeclarationContainerScope`) is
        placed **directly** under `com/example`; those go in `groups/` and `roles/`. Because it is
        `scope = DirectOnly`, this constraint does not descend into the files inside `groups/` and
        `roles/`.
    """.trimIndent()
    forbiddenContents = """
        - Application code. Creating `src/main/kotlin` in `:architecture-test` fails as files that
          no role covers
        - Anything that already has a layer it belongs to. This is a place to write only "shape"
    """.trimIndent()
    example("ProjectArchitecture.kt", "The entry point of the definition")
    example("roles/ControllerRole.kt", "The declaration of a single role")
    example("ProjectArchitectureTest.kt", "The test that asserts the definition")
    layout {
        // The price of the recommended setup: `:architecture-test` checks itself, so
        // the definition has to give itself a role like everything else. The module
        // path resolves to `architecture-test/`, which is where the files actually are.
        //
        // `**` covers `groups/` and `roles/` without naming them, so splitting the
        // definition further costs no line here — and buys no enforcement either: a file
        // under `roles/` that declares no role passes just the same.
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" {
                // The samples' one `DirectOnly` constraint (katachi's guide: "Konsist
                // integration"). With the default scope, `Subtree`, it would also cover `groups/` and
                // `roles/`, where every file is exactly such a declaration, and fail.
                "Must not declare groups or roles directly here".konsist(scope = DirectOnly) {
                    functions().mustNot { it.receiverType?.name == "DeclarationContainerScope" }
                }
                "*".ktFile()
                "**" / "*".ktFile()
            }
        }
    }
}
