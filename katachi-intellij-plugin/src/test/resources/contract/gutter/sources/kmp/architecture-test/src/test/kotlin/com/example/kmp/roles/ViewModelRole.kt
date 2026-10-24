@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * The state holder of one screen, named after the feature module it sits in.
 *
 * Written the same loose way as [screen]: a `*ViewModel.kt` glob under the directory
 * `"feature" / "*"`, with the file name named after the module by convention.
 */
fun DeclarationContainerScope.viewModel() = "ViewModel".role {
    title = "ViewModel"
    summary = "An androidx.lifecycle.ViewModel that holds screen state. " +
        "Converts values fetched from the Repository into a UiState and exposes it as a StateFlow"
    description = """
        The place that holds screen state. One file, `<Name>ViewModel.kt`, in the `commonMain` of
        `:feature:<name>`. As with Screen, the file name is bound to the module name, so two
        ViewModels never sit side by side in one feature module.

        It extends `androidx.lifecycle.ViewModel`, which is the Compose Multiplatform version
        (`org.jetbrains.androidx.lifecycle`), so it can be written in `commonMain` and works on
        iOS too. A package name starting with `androidx` does not make it Android-only, and this
        is an easy place to trip in KMP.

        It only receives the Repository as an argument and never creates it. Creating it is the Route's job.
    """.trimIndent()
    allowedContents = """
        - `private val mutableState = MutableStateFlow(...)` and a `val state: StateFlow<UiState<...>>`
          that exposes it with `asStateFlow()`
        - Operations called from the screen (such as `reload()`) and loading with `viewModelScope`
        - A data class shaped for display (like `SettingsUi`; it may live in the same file)
    """.trimIndent()
    forbiddenContents = """
        - `@Composable`. Drawing is the Screen's job
        - `android.*` imports and `Context`. Push whatever needs them down to ExpectDeclaration and ActualImplementation
          (expect/actual) in `:data`; written here, `commonMain` would not compile
        - Dependencies on the ViewModel of another feature
    """.trimIndent()
    example("HomeViewModel", "The state of the home screen")
    example("SettingsViewModel", "The state of the settings screen")
    layout {
        "feature" / "*" / "commonMain".sourceSet / kotlin / "com/example/kmp/feature" / "*" /
            "*ViewModel".ktFile()
    }
    // The state holder named after the file, exposing `state` and taking events, and a display
    // type such as SettingsUi beside it. androidx.compose and android are not among the imports,
    // so a view model that reaches for either fails.
    "Must not import Compose or Android".ktFileContent {
        `import`("androidx.lifecycle.**")
        `import`("kotlinx.coroutines.**")
        `import`("com.example.kmp.data.**")
        `import`("com.example.kmp.ui.core.**")

        allow(internal, data, `class`, "*Ui")
        allow(internal, `class`, fileName).superclass("ViewModel") {
            allow(public, `val`, "state", "StateFlow<UiState<*>>")
            allow(public, `fun`, "*")    // the events: reload()
            allow(private, anythingElse) // the repository and the mutable state
        }
    }
}
