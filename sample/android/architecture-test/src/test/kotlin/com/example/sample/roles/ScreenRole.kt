@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import com.example.sample.allowedContents
import com.example.sample.forbiddenContents
import com.example.sample.groups.featureSources
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * The role of one screen's UI: the `@Composable` its feature module is named after.
 *
 * The layout names the module directory and the package directory alike, `capture("feature")`,
 * and derives the file name from it: `${capture("feature").pascalCase}Screen`. So `:feature:home`
 * may hold `HomeScreen.kt` and nothing else named `*Screen.kt`, without the feature modules
 * being listed one by one.
 */
fun DeclarationContainerScope.screen() = "Screen".role {
    title = "Screen"
    summary = "A @Composable that implements the UI of one screen. One <Name>Screen.kt per :feature:<name>"
    description = """
        The `@Composable` that draws the screen itself. The convention is
        that `:feature:home` has one `HomeScreen.kt`, named after the module, and the check
        holds the file to it: the module directory, the package directory and the file name all
        read the same feature name. When there are two screens, split them into separate
        feature modules.

        Two `HomeScreen`s sit stacked in one file, both `internal`. The one called from
        navigation takes `viewModel()` as a default argument, collects state with
        `collectAsStateWithLifecycle()` and just hands it to the other. The other is a stateless
        function that takes only `UiState<HomeContent>` and callbacks, and it is what `@Preview`
        touches.

        A `@Preview` is a `private` `@Composable` at the end of the same file, with the body
        wrapped in `PreviewRoot { }` from `:ui`. It passes only state:
        `HomeScreenContentPreview` passes `UiState.Content(...)` and `HomeScreenLoadingPreview`
        passes `UiState.Loading`, lining up different states of the same screen. The overload
        that takes `viewModel()` is not previewed. `ktFileContent { }` checks the shape of the
        file (`internal` screens named after it, `private` previews, and what it imports); what
        a preview passes and wraps its body in is not checked.
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
        // `feature` is the module directory and the package directory alike, and the file name
        // follows it: feature/home/.../home/HomeScreen.kt, and nothing else named *Screen.kt there.
        featureSources(capture("feature")) / "${capture("feature").pascalCase}Screen".ktFile()
    }
    // The two overloads named after the file, both internal, and the previews, all private.
    // Navigation and other features are not among the imports: leaving a screen is a callback.
    ktFileContent {
        `import`("androidx.compose.**")
        `import`("androidx.lifecycle.**")
        `import`("com.example.sample.ui.**")

        annotated("Composable").internal.`fun`(fileName)
        annotated("Preview").private.`fun`("*Preview")
    }
}
