package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of a file that declares one role of the definition. */
fun DeclarationContainerScope.roleDefinition() = "RoleDefinition" {
    title = "Role definition"
    summary = "One `roles/<Name>Role.kt` per role, holding its description, its layout and its constraints"
    description = """
        A role is declared as an extension function on `DeclarationContainerScope`, one per
        file, so the description, the `layout { }` and the constraints of a role are read in one
        place. The file name is `*Role.kt`; a file in `roles/` that is not a role is reported.

        A role that only a role needs (a private helper such as `ServiceRole`'s constraint) stays
        in the file that uses it, so the declaration site a violation names is the role that
        owns the rule.
    """.trimIndent()
    forbiddenContents = """
        - A group. Those go in `groups/`
    """.trimIndent()
    example("roles/ControllerRole.kt", "The declaration of a single role")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" / "roles" / "*Role".ktFile()
        }
    }
}
