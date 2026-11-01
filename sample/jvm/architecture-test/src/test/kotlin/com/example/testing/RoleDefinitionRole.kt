package com.example.testing

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

fun DeclarationContainerScope.roleDefinition() = "RoleDefinition".role {
    title = "Role definition"
    summary = "One `<Name>Role.kt` per role, in the directory of the group that calls it, holding its description, its layout and its constraints"
    description = """
        A role is declared as an extension function on `DeclarationContainerScope`, one per
        file, so the description, the `layout { }` and the constraints of a role are read in one
        place. The file name is `*Role.kt`. A role sits in the directory of the
        group that calls it (`api/ControllerRole.kt` for `apiGroup()`), and a role that no group
        calls sits next to the entry; a `*Role.kt` placed anywhere else is reported.

        A role that only a role needs (a private helper such as `ServiceRole`'s constraint) stays
        in the file that uses it, so the declaration site a violation names is the role that
        owns the rule.
    """.trimIndent()
    forbiddenContents = """
        - A group. Those go in `<Name>Group.kt`, in the directory named after the group
    """.trimIndent()
    example("api/ControllerRole.kt", "The declaration of a single role")
    layout {
        "architecture-test" / testSourceSet / kotlin / "com/example" / "**" / "*Role".ktFile()
    }
}
