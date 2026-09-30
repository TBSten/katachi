package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of a file that declares one role of the definition. */
fun DeclarationContainerScope.roleDefinition() = "RoleDefinition" {
    title = "Role definition"
    summary = "roles/<Name>Role.kt, one role of the definition, with where its files live"
    description = """
        One role of the definition per file, in `roles/<Name>Role.kt`, and the file name says
        which role it is (the role `"UiCore"` is in `roles/UiCoreRole.kt`). Only `*Role.kt` may
        sit in `roles/`, so a shared helper slipping in as another kind of file becomes an
        `[UnexpectedFile]`.

        Not called from anywhere on its own: add the call to the group this role belongs to
        once it says something.
    """.trimIndent()
    example("roles/ScreenRole.kt", "The declaration of the Screen role")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/kmp" / "roles" / "*Role".ktFile()
        }
    }
}
