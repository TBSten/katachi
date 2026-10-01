@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import com.example.sample.allowedContents
import com.example.sample.forbiddenContents
import com.example.sample.groups.featureSources
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role of one screen's UI: the `@Composable` its feature module is named after.
 *
 * The layout is loosened by directory: `"feature" / "*" / … / "*Screen"`. Any feature module
 * may hold a `<Name>Screen.kt`; the check does not tie the file name to the module name, and it
 * does not list the feature modules one by one.
 */
fun DeclarationContainerScope.screen() = "Screen" {
    title = "Screen"
    summary = "A @Composable that implements the UI of one screen. One <Name>Screen.kt per :feature:<name>"
    description = """
        The `@Composable` that draws the screen itself. The convention is
        that `:feature:home` has one `HomeScreen.kt`, named after the module. The check only
        looks at the place and the suffix (`*Screen.kt` in any feature module), so the pairing of module
        name and file name is kept by review. When there are two screens, split them into
        separate feature modules.

        Two `HomeScreen`s sit stacked in one file, both `internal`. The one called from
        navigation takes `viewModel()` as a default argument, collects state with
        `collectAsStateWithLifecycle()` and just hands it to the other. The other is a stateless
        function that takes only `UiState<HomeContent>` and callbacks, and it is what `@Preview`
        touches.

        A `@Preview` is a `private` `@Composable` at the end of the same file, with the body
        wrapped in `PreviewRoot { }` from `:ui`. It passes only state:
        `HomeScreenContentPreview` passes `UiState.Content(...)` and `HomeScreenLoadingPreview`
        passes `UiState.Loading`, lining up different states of the same screen. The overload
        that takes `viewModel()` is not previewed. This convention is not checked in this
        sample.
    """.trimIndent()
    allowedContents = """
        - The screen layout, and switching between `Loading` / `Content` / `Error` of `UiState`
        - Shared components from `:ui` (such as `AppButton`) and calls to Material3
        - The `@Preview` for this screen (a `private` function in this file, wrapped in `PreviewRoot { }`)
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
        featureSources() / "*Screen".ktFile()
    }
}
