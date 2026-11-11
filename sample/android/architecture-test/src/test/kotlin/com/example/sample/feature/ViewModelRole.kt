@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.feature

import com.example.sample.feature.featureSources
import com.example.sample.forbiddenContents
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.konsist.declarations.ktFileContent

fun DeclarationContainerScope.viewModel() = "ViewModel".role {
    title = "ViewModel"
    summary = "An androidx.lifecycle.ViewModel that exposes screen state as a StateFlow and receives events"
    description = """
        One `<Name>ViewModel.kt` per `:feature:<name>`. It exposes the screen state as
        `StateFlow<UiState<T>>` and emits the next state on receiving events from the screen
        (`refresh()`, `setDarkThemeEnabled(enabled)`). As with the Screen, the file name is
        `<Name>ViewModel.kt`, where `<Name>` is the feature's name.

        The state container is `UiState` from the `core` package of `:ui`, and the content its
        `Content` wraps (`HomeContent` / `SettingsContent`) is a data class in the same file as
        the ViewModel. It is a type used by a single screen, so it is not lifted into `:ui`.

        The Repository is received as a constructor argument, with the production implementation
        as the default (`userRepository: UserRepository = UserRepositoryImpl()`). This lets
        `viewModel()` build it without a factory; if DI is introduced, only this default
        disappears and the shape does not change.
    """.trimIndent()
    forbiddenContents = """
        - Dependencies on Compose. It imports no `androidx.compose.*` and writes no
          `@Composable`. Because this line is drawn, state assembly can be read without Compose
        - Android's Context / View / resources. Apart from extending
          `androidx.lifecycle.ViewModel`, it does not touch Android
        - The screen layout. Drawing is the Screen's job
    """.trimIndent()
    example("HomeViewModel", "The state of the home screen")
    example("SettingsViewModel", "The state of the settings screen")
    // A new feature's ViewModel and its `<Name>Content`, standing on the existing UserRepository
    // so that it compiles in any feature. Usually generated with its Screen as the set
    // `feature.NewFeature`.
    //   ./gradlew :architecture-test:katachiTemplate --arg template=feature.ViewModel --arg feature=profile
    layout {
        // `feature` is the module directory and the package directory alike, and the file name
        // follows it: feature/home/.../home/HomeViewModel.kt, and nothing else named *ViewModel.kt there.
        featureSources(capture("feature")) / "${capture("feature").pascalCase}ViewModel".ktFile()
            .template {
                val feature = captureValue("feature")
                val screen = "${feature.pascalCase}Screen"
                val content = "${feature.pascalCase}Content"
                val viewModel = "${feature.pascalCase}ViewModel"
                """
                    |package com.example.sample.feature.$feature
                    |
                    |import androidx.lifecycle.ViewModel
                    |import com.example.sample.data.user.UserRepository
                    |import com.example.sample.data.user.UserRepositoryImpl
                    |import com.example.sample.ui.core.UiState
                    |import kotlinx.coroutines.flow.MutableStateFlow
                    |import kotlinx.coroutines.flow.StateFlow
                    |import kotlinx.coroutines.flow.asStateFlow
                    |
                    |/** What [$screen] draws once it has something to draw. */
                    |internal data class $content(
                    |    val userName: String,
                    |)
                    |
                    |/** State holder of [$screen]. */
                    |internal class $viewModel(
                    |    private val userRepository: UserRepository = UserRepositoryImpl(),
                    |) : ViewModel() {
                    |    private val mutableUiState = MutableStateFlow<UiState<$content>>(UiState.Loading)
                    |
                    |    /** State of the $feature screen, collected by the composable. */
                    |    val uiState: StateFlow<UiState<$content>> = mutableUiState.asStateFlow()
                    |
                    |    init {
                    |        refresh()
                    |    }
                    |
                    |    /** Reads the current user again and republishes the screen state. */
                    |    fun refresh() {
                    |        mutableUiState.value = UiState.Content(
                    |            $content(userName = userRepository.currentUserName()),
                    |        )
                    |    }
                    |}
                """.trimMargin()
            }
    }
    // The description above, written the way the file is written. androidx.compose and android
    // are not among the imports, so a view model that reaches for either fails.
    "Must not import Compose or Android".ktFileContent {
        `package`("com.example.sample.feature.${captureValue("feature")}")

        `import`("androidx.lifecycle.**")
        `import`("kotlinx.coroutines.**")
        `import`("com.example.sample.data.**")
        `import`("com.example.sample.ui.core.**")

        val content = fileName.replace("ViewModel", "Content")      // HomeViewModel.kt -> HomeContent
        required(internal, data, `class`, content) { // data class HomeContent(val userName: String, ...)
            allow(`val`, "*")                         // read-only values only: the screen does not write back
        }
        required(internal, `class`, fileName).superclass("ViewModel") {
            required(public, `val`, "uiState").type("StateFlow<UiState<$content>>")
            allow(init)                  // the first load
            allow(public, `fun`, "*")    // the events: refresh(), ...
            allow(private, anythingElse) // the repository and the mutable state
        }
    }
}
