@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.featureSources
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.konsist.declarations.ktFileContent

/** The role holding one screen's state, named after the feature module it belongs to. */
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
    layout {
        // `feature` is the module directory and the package directory alike, and the file name
        // follows it: feature/home/.../home/HomeViewModel.kt, and nothing else named *ViewModel.kt there.
        featureSources(capture("feature")) / "${capture("feature").pascalCase}ViewModel".ktFile()
    }
    // The description above, written the way the file is written. androidx.compose and android
    // are not among the imports, so a view model that reaches for either fails.
    "Must not import Compose or Android".ktFileContent {
        `import`("androidx.lifecycle.**")
        `import`("kotlinx.coroutines.**")
        `import`("com.example.sample.data.**")
        `import`("com.example.sample.ui.core.**")

        val content = fileName.replace("ViewModel", "Content")      // HomeViewModel.kt -> HomeContent
        allow(internal, data, `class`, content)
        allow(internal, `class`, fileName).superclass("ViewModel") {
            allow(public, `val`, "uiState", "StateFlow<UiState<$content>>")
            allow(public, `fun`, "*")    // the events: refresh(), ...
            allow(private, anythingElse) // the repository and the mutable state
        }
    }
}
