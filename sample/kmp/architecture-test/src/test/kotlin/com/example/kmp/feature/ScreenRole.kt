@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.feature

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

fun DeclarationContainerScope.screen() = "Screen".role {
    title = "Screen"
    summary = "The @Composable of one screen. Subscribes to the ViewModel's StateFlow and draws by combining Components"
    description = """
        The look of one whole screen. Put one file, `<Name>Screen.kt`, in the
        `commonMain` of `:feature:<name>`, named after the module (`HomeScreen.kt` in
        `:feature:home`). The check holds the file to it: the module directory, the package
        directory and the file name all read the same feature name. To add a screen, add a module.

        The file is built in two levels. `HomeScreen` receives the ViewModel and subscribes with
        `collectAsState()`, and `internal fun HomeContent` receives a `UiState` as an argument and
        draws it. Separating what cannot be drawn without creating a ViewModel from what can be
        drawn once given a state lets both previews (ScreenPreview) and tests deal with the latter only.

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
        // `feature` is the module directory and the package directory alike, and the file name
        // follows it: feature/home/.../home/HomeScreen.kt, and nothing else named *Screen.kt there.
        "feature" / capture("feature") / "commonMain".sourceSet / kotlin / "com/example/kmp/feature" /
            capture("feature") / "${capture("feature").pascalCase}Screen".ktFile()
    }
}
