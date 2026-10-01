package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The UI foundation no screen owns: the `core` package of `:ui`. */
fun DeclarationContainerScope.uiCore() = "UiCore" {
    title = "UI core"
    summary = "The core package of the :ui module. The screen-independent foundation of the UI, such as UiState"
    description = """
        A package for the vocabulary of the UI layer that is neither look nor component. Right now
        it holds only `UiState`, a sealed interface for the three states every screen shares:
        loading, loaded and failed.

        `UiState` deliberately does not depend on Compose. It is the type at both ends, created by
        the ViewModel and read by the `@Composable`, so keeping it plain Kotlin lets both sides
        be tested without the Compose runtime. In fact `SampleModulesSpec` of `:app:android`
        checks the behavior of `valueOrNull()` without starting Compose.

        The name `core` says nothing beyond "foundation", so it tends to become a place where
        anything goes. Judge by whether it satisfies both "knows no screen" and "knows no Compose".
    """.trimIndent()
    allowedContents = """
        - Types that represent screen state, and their small extension functions
        - Vocabulary used in common by several screens, on the UI side only
    """.trimIndent()
    forbiddenContents = """
        - `@Composable`. Components go in `component` and the look in `theme`
        - Domain types. Users and their lists belong to `:data`, and `UiState` only wraps them
        - State meaningful to only one screen. Write it on the feature module side
    """.trimIndent()
    example("UiState", "The type that represents screen state")
    example("valueOrNull", "The extension function that extracts the value")
    layout {
        "ui" {
            "commonMain".sourceSet / kotlin / "com/example/kmp/ui" / "core" / "*".ktFile()
        }
    }
}
