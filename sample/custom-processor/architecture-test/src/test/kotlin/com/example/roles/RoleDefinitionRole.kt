package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of a role declared in the katachi DSL. */
fun DeclarationContainerScope.roleDefinition() = "RoleDefinition" {
    title = "Role definition"
    summary = "One role of the katachi definition, declared in `roles/<Name>Role.kt`"
    description = """
        One role per file, named after the role with a `Role` suffix. Each holds the role's title,
        summary, description and examples, and the `layout { }` that says which files belong to it.
    """.trimIndent()
    forbiddenContents = "Group declarations. A group belongs to the group definition role, in `groups/`."
    example("roles/StoreRole.kt", "The declaration of a single role")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" / "roles" / "*Role".ktFile()
        }
    }
}
