package com.example.sample.roles

import com.example.sample.allowedContents
import com.example.sample.forbiddenContents
import com.example.sample.groups.featureSources
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The role of one screen's UI: the `@Composable` its feature module is named after.
 *
 * `wildcard("feature")` is the module's own name, so a file is not merely allowed to be *a*
 * screen but has to be **that module's** screen: `:feature:home` may hold `HomeScreen.kt` and
 * nothing else called `*Screen.kt`.
 */
fun DeclarationContainerScope.screen() = "Screen" {
    title = "Screen"
    summary = "A @Composable that implements the UI of one screen. One <Name>Screen.kt per :feature:<name>"
    description = """
        The `@Composable` that draws the screen itself. The correspondence is fixed:
        `:feature:home` has exactly one `HomeScreen.kt`, and the file name is decided by the
        module name. You cannot put a `ProfileScreen.kt` in `:feature:home`, and you cannot
        delete `HomeScreen.kt`. When there are two screens, split them into separate feature
        modules.

        Two `HomeScreen`s sit stacked in one file, both `internal`. The one called from
        navigation takes `viewModel()` as a default argument, collects state with
        `collectAsStateWithLifecycle()` and just hands it to the other. The other is a stateless
        function that takes only `UiState<HomeContent>` and callbacks, and it is what `@Preview`
        touches.
    """.trimIndent()
    allowedContents = """
        - The screen layout, and switching between `Loading` / `Content` / `Error` of `UiState`
        - Shared components from `:ui` (such as `AppButton`) and calls to Material3
        - The `@Preview` for this screen (see the Preview role)
    """.trimIndent()
    forbiddenContents = """
        - Building and holding state. That is the ViewModel's job, and state types such as
          `HomeContent` also go in the same file as the ViewModel
        - Dependencies on `NavHostController`. Navigation out of a screen only calls the
          callbacks received as arguments (`onNavigateToSettings` / `onNavigateUp`)
        - Types of other features. Features do not refer to each other; `:app` connects them through the Route
    """.trimIndent()
    example("HomeScreen", "The home screen")
    example("SettingsScreen", "The settings screen")
    layout {
        ":feature:${capture("feature")}".module {
            featureSources() / "${wildcard("feature").pascalCase}Screen".ktFile()
        }
    }
}
