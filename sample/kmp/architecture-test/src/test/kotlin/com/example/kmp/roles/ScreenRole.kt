@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role of one screen's UI: the `@Composable` its feature module is named after.
 *
 * Written loosely, as the directory `"feature" / "*"` and a `*Screen.kt` glob: any feature
 * module may hold a `<Name>Screen.kt`. The check does not tie the file name to the module name
 * (a `ProfileScreen.kt` under `:feature:home` is accepted, and so is a module without a
 * screen), so that pairing is kept by review.
 */
fun DeclarationContainerScope.screen() = "Screen" {
    title = "Screen"
    summary = "The @Composable of one screen. Subscribes to the ViewModel's StateFlow and draws by combining Components"
    description = """
        The look of one whole screen. Put one file, `<Name>Screen.kt`, in the
        `commonMain` of `:feature:<name>`, named after the module (`HomeScreen.kt` in
        `:feature:home`). The check looks at the place and the suffix only. To add a screen,
        add a module.

        The file is built in two levels. `HomeScreen` receives the ViewModel and subscribes with
        `collectAsState()`, and `internal fun HomeContent` receives a `UiState` as an argument and
        draws it. Separating what cannot be drawn without creating a ViewModel from what can be
        drawn once given a state lets both previews (ui/Preview) and tests deal with the latter only.

        It is `commonMain` only so that Android and iOS share the same screen code through
        Compose Multiplatform. If something needs platform-dependent behavior, push it down to
        ExpectDeclaration / ActualImplementation (expect/actual) in `:data`, not into a screen.
    """.trimIndent()
    allowedContents = """
        - The `@Composable` of that screen, and a stateless `<Name>Content` that receives state as an argument
        - A description of the placement that combines Component / Theme / UiCore from `:ui`
    """.trimIndent()
    forbiddenContents = """
        - Direct calls to a Repository. The ViewModel turns data into a `UiState` before handing it over
        - Screens of other feature modules, or their internal types
        - `androidMain` / `iosMain` versions of a screen. This role declares only `commonMain`
    """.trimIndent()
    example("HomeScreen", "The home screen")
    example("SettingsScreen", "The settings screen")
    layout {
        "feature" / "*" / "commonMain".sourceSet / kotlin / "com/example/kmp/feature" / "*" /
            "*Screen".ktFile()
    }
}
