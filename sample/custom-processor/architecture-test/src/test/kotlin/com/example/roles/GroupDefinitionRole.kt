package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of a group declared in the katachi DSL. */
fun DeclarationContainerScope.groupDefinition() = "GroupDefinition" {
    title = "Group definition"
    summary = "One group of the katachi definition, declared in `groups/<Name>Group.kt`"
    description = """
        One group per file, named after the group with a `Group` suffix. The function inside says what
        the group is made of by calling the role functions in order.

        None of those functions may be `inline`: katachi captures the declaration site from the stack,
        and an inlined frame reports the caller's file with a line number past its end.
    """.trimIndent()
    forbiddenContents = "Role declarations. A role belongs to the role definition role, in `roles/`."
    example("groups/CoreGroup.kt", "A group made of three roles")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" / "groups" / "*Group".ktFile()
        }
    }
}
