package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of a file that declares one group of the definition. */
fun DeclarationContainerScope.groupDefinition() = "GroupDefinition" {
    title = "Group definition"
    summary = "One `groups/<Name>Group.kt` per group, saying what the group is made of by calling role functions"
    description = """
        A group is declared as an extension function on `DeclarationContainerScope`, the scope
        that `architecture { }` and `"...".group { }` share. Because a group only calls the
        role functions, a role can be moved into another group without touching the role's own
        file.

        The file name is `*Group.kt`, so a file in `groups/` that is not a group is reported.
        Nothing here may be `inline`: katachi captures the declaration site from the stack, and
        an inlined frame would point at a line nobody wrote (`ProjectArchitectureSpec` is what
        holds that line).
    """.trimIndent()
    forbiddenContents = """
        - A role. Those go in `roles/`
    """.trimIndent()
    example("groups/DomainGroup.kt", "A group that lists the roles of the domain layer")
    layout {
        "architecture-test" / testSourceSet / kotlin / "com/example" / "groups" / "*Group".ktFile()
    }
}
