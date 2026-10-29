package com.example.testing

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of a role declared in the katachi DSL. */
fun DeclarationContainerScope.roleDefinition() = "RoleDefinition".role {
    title = "Role definition"
    summary = "One role of the katachi definition, declared in `<Name>Role.kt`, in the directory of the group that calls it"
    description = """
        One role per file, named after the role with a `Role` suffix. Each holds the role's title,
        summary, description and examples, and the `layout { }` that says which files belong to it.
    """.trimIndent()
    forbiddenContents = "Group declarations. A group belongs to the group definition role, in the directory named after the group."
    example("core/StoreRole.kt", "The declaration of a single role")
    layout {
        "architecture-test" / testSourceSet / kotlin / "com/example" / "**" / "*Role".ktFile()
    }
}
