package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of a file that declares one group of the definition. */
fun DeclarationContainerScope.groupDefinition() = "GroupDefinition" {
    title = "Group definition"
    summary = "groups/<Name>Group.kt, one group of the definition, which lists its roles by calling their functions"
    description = """
        One group of the definition per file, in `groups/<Name>Group.kt`, and the file name
        says which group it is (the group `"app"` is in `groups/AppGroup.kt`). A group says what
        it is made of by calling the role functions in order. Only `*Group.kt` may sit in
        `groups/`, so a shared helper slipping in as another kind of file becomes an
        `[UnexpectedFile]`.

        The layout takes `*` for the name, so adding a group passes without touching the
        definition. The group still has to be called from `ProjectArchitecture.kt` to say
        anything.
    """.trimIndent()
    example("groups/UiGroup.kt", "The declaration of the ui group")
    layout {
        "architecture-test" {
            testSourceSet / kotlin / "com/example/kmp" / "groups" / "*Group".ktFile()
        }
    }
}
