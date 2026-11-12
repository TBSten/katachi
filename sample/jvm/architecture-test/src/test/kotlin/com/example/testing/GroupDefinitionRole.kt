@file:OptIn(ExperimentalKatachiApi::class)

package com.example.testing

import com.example.forbiddenContents
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.diff.diffWeight
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

fun DeclarationContainerScope.groupDefinition() = "GroupDefinition".role {
    title = "Group definition"
    summary = "One `<Name>Group.kt` per group, in the directory named after the group, saying what the group is made of by calling role functions"
    description = """
        A group is declared as an extension function on `DeclarationContainerScope`, the scope
        that `architecture { }` and `"...".group { }` share. Because a group only calls the
        role functions, a role moves into another group by moving its file, with its body
        unchanged.

        The file name is `*Group.kt`, and the file sits in the directory named after the group
        (`domain/DomainGroup.kt` for `"Domain".group { }`), so the roles the group calls are its
        neighbours. A `*Group.kt` placed anywhere else is reported.
        Nothing here may be `inline`: katachi captures the declaration site from the stack, and
        an inlined frame would point at a line nobody wrote. `ProjectArchitectureSpec` checks
        that each captured line holds its declaration.
    """.trimIndent()
    forbiddenContents = """
        - A role. Those go in `<Name>Role.kt`, in the directory of the group that calls it
    """.trimIndent()
    example("domain/DomainGroup.kt", "A group that lists the roles of the domain layer")
    // A change to the definition changes what every check and page says, so katachiDiff
    // puts it at the top of the heat.
    diffWeight = 10.0
    layout {
        "architecture-test" / testSourceSet / kotlin / "com/example" / "**" / "*Group".ktFile()
    }
}
