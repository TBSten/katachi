@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.testing

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.diff.diffWeight
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

fun DeclarationContainerScope.roleDefinition() = "RoleDefinition".role {
    title = "Role definition"
    summary = "<group>/<Name>Role.kt, one role of the definition, with where its files live"
    description = """
        One role of the definition per file, in `<group>/<Name>Role.kt`, and the file name says
        which role it is (the role `"UiCore"` is in `ui/UiCoreRole.kt`, because `uiGroup()`
        calls `uiCore()`). The directory is the one of the group that calls the role. A role
        that no group calls sits next to `ProjectArchitecture.kt`. A file that is not a
        `*Group.kt` or `*Role.kt` in a group directory, such as a shared helper, becomes an
        `[UnexpectedFile]`.

        Not called from anywhere on its own: add the call to the group this role belongs to
        once it says something.
    """.trimIndent()
    example("feature/ScreenRole.kt", "The declaration of the Screen role")
    // A change to the definition changes what every check and page says, so katachiDiff
    // puts it at the top of the heat.
    diffWeight = 10.0
    layout {
        "architecture-test" {
            testSourceSet / kotlin / "com/example/kmp" / "**" / "*Role".ktFile()
        }
    }
}
