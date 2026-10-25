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
import me.tbsten.katachi.dsl.template
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
    // A new feature's Screen in the two overloads above, with its two previews. It draws the
    // `<Name>Content` of the ViewModel template, so the two are generated together, usually as the
    // set `feature.NewFeature`.
    //   ./gradlew :architecture-test:katachiTemplate --arg template=feature.Screen --arg feature=profile
    layout {
        // `feature` is the module directory and the package directory alike, and the file name
        // follows it: feature/home/.../home/HomeScreen.kt, and nothing else named *Screen.kt there.
        featureSources(capture("feature")) / "${capture("feature").pascalCase}Screen".ktFile()
            .template {
                val feature = captureValue("feature")
                val screen = "${feature.pascalCase}Screen"
                val content = "${feature.pascalCase}Content"
                val viewModel = "${feature.pascalCase}ViewModel"
                """
                    |package com.example.sample.feature.$feature
                    |
                    |import androidx.compose.foundation.layout.Arrangement
                    |import androidx.compose.foundation.layout.Column
                    |import androidx.compose.foundation.layout.fillMaxSize
                    |import androidx.compose.foundation.layout.padding
                    |import androidx.compose.material3.CircularProgressIndicator
                    |import androidx.compose.material3.MaterialTheme
                    |import androidx.compose.material3.Text
                    |import androidx.compose.runtime.Composable
                    |import androidx.compose.runtime.getValue
                    |import androidx.compose.ui.Alignment
                    |import androidx.compose.ui.Modifier
                    |import androidx.compose.ui.tooling.preview.Preview
                    |import androidx.compose.ui.unit.dp
                    |import androidx.lifecycle.compose.collectAsStateWithLifecycle
                    |import androidx.lifecycle.viewmodel.compose.viewModel
                    |import com.example.sample.ui.component.AppButton
                    |import com.example.sample.ui.component.AppButtonEmphasis
                    |import com.example.sample.ui.core.UiState
                    |import com.example.sample.ui.preview.PreviewRoot
                    |
                    |/** The $feature screen, wired to its [$viewModel]. */
                    |@Composable
                    |internal fun $screen(
                    |    onNavigateUp: () -> Unit,
                    |    modifier: Modifier = Modifier,
                    |    viewModel: $viewModel = viewModel(),
                    |) {
                    |    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    |    $screen(
                    |        uiState = uiState,
                    |        onRefresh = viewModel::refresh,
                    |        onNavigateUp = onNavigateUp,
                    |        modifier = modifier,
                    |    )
                    |}
                    |
                    |/** The $feature screen as a function of its state. */
                    |@Composable
                    |internal fun $screen(
                    |    uiState: UiState<$content>,
                    |    onRefresh: () -> Unit,
                    |    onNavigateUp: () -> Unit,
                    |    modifier: Modifier = Modifier,
                    |) {
                    |    Column(
                    |        modifier = modifier
                    |            .fillMaxSize()
                    |            .padding(24.dp),
                    |        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    |        horizontalAlignment = Alignment.CenterHorizontally,
                    |    ) {
                    |        when (uiState) {
                    |            UiState.Loading -> CircularProgressIndicator()
                    |
                    |            is UiState.Content -> {
                    |                Text(
                    |                    text = uiState.value.userName,
                    |                    style = MaterialTheme.typography.headlineSmall,
                    |                )
                    |                AppButton(text = "Reload", onClick = onRefresh)
                    |            }
                    |
                    |            is UiState.Error -> Text(
                    |                text = uiState.message,
                    |                style = MaterialTheme.typography.bodyMedium,
                    |                color = MaterialTheme.colorScheme.error,
                    |            )
                    |        }
                    |
                    |        AppButton(
                    |            text = "Back",
                    |            onClick = onNavigateUp,
                    |            emphasis = AppButtonEmphasis.Outlined,
                    |        )
                    |    }
                    |}
                    |
                    |@Preview(showBackground = true)
                    |@Composable
                    |private fun ${screen}ContentPreview() {
                    |    PreviewRoot {
                    |        $screen(
                    |            uiState = UiState.Content($content(userName = "katachi")),
                    |            onRefresh = {},
                    |            onNavigateUp = {},
                    |        )
                    |    }
                    |}
                    |
                    |@Preview(showBackground = true)
                    |@Composable
                    |private fun ${screen}LoadingPreview() {
                    |    PreviewRoot {
                    |        $screen(
                    |            uiState = UiState.Loading,
                    |            onRefresh = {},
                    |            onNavigateUp = {},
                    |        )
                    |    }
                    |}
                """.trimMargin()
            }
    }
    // The two overloads named after the file, both internal, and the previews, all private.
    // Navigation and other features are not among the imports: leaving a screen is a callback.
    ktFileContent {
        `package`("com.example.sample.feature.${captureValue("feature")}")

        `import`("androidx.compose.**")
        `import`("androidx.lifecycle.**")
        `import`("com.example.sample.ui.**")

        required(annotated("Composable"), internal, `fun`, fileName)
        allow(annotated("Preview"), private, `fun`, "*Preview")
    }
}
