package com.example.testing

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

fun DeclarationContainerScope.groupDefinition() = "GroupDefinition".role {
    title = "Group definition"
    summary = "One group of the katachi definition, declared in `<Name>Group.kt`, in the directory named after the group"
    description = """
        One group per file, named after the group with a `Group` suffix. The function inside says what
        the group is made of by calling the role functions in order.

        None of those functions may be `inline`: katachi captures the declaration site from the stack,
        and an inlined frame reports the caller's file with a line number past its end.
    """.trimIndent()
    forbiddenContents = "Role declarations. A role belongs to the role definition role, in the directory of the group that calls it."
    example("core/CoreGroup.kt", "A group made of three roles")
    layout {
        "architecture-test" / testSourceSet / kotlin / "com/example" / "**" / "*Group".ktFile()
    }
}
