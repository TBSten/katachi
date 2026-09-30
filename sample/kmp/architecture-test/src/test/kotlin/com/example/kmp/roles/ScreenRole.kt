@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The role of one screen's UI: the `@Composable` its feature module is named after.
 *
 * `wildcard("feature")` is what `:feature:*` matched — `home` for `:feature:home` — so the file
 * required of that module is `HomeScreen.kt` and nothing else: a `ProfileScreen.kt` under
 * `:feature:home` is an `[UnexpectedFile]`, and it is `[MissingFile]` that reports a feature
 * whose screen was renamed. Written as a plain `*Screen.kt` glob the check would have accepted
 * both.
 */
fun DeclarationContainerScope.screen() = "Screen" {
    title = "Screen"
    summary = "The @Composable of one screen. Subscribes to the ViewModel's StateFlow and draws by combining Components"
    description = """
        The look of one whole screen. Put exactly one file, `<Name>Screen.kt`, in the
        `commonMain` of `:feature:<name>`. The file name is decided by the module name, so
        `ProfileScreen.kt` cannot be added to `:feature:home`. To add a screen, add a module.

        The file is built in two levels. `HomeScreen` receives the ViewModel and subscribes with
        `collectAsState()`, and `internal fun HomeContent` receives a `UiState` as an argument and
        draws it. Separating what cannot be drawn without creating a ViewModel from what can be
        drawn once given a state lets both previews (ui/Preview) and tests deal with the latter only.

        It is `commonMain` only so that Android and iOS share the same screen code through
        Compose Multiplatform. If something needs platform-dependent behavior, push it down to
        PlatformImplementation (expect/actual) in `:data`, not into a screen.
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
        ":feature:${capture("feature")}".module {
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcard("feature").pascalCase}Screen".ktFile()
        }
    }
}
