@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.feature

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.ui.component
import com.example.kmp.ui.componentPreview
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent

fun DeclarationContainerScope.screenPreview() = "ScreenPreview".role {
    title = "Screen preview"
    summary = "A private @Composable annotated with @Preview for a screen. Placed in <Target>Preview.kt in the feature " +
        "module that holds the screen, with the content wrapped in PreviewRoot"
    description = """
        A `private @Composable` annotated with `@Preview` that draws a screen. It is written in
        `<Target>Preview.kt` in the same package as the screen, inside the feature module that
        holds the screen. Keeping it in the same file as the target is also possible, but this
        sample splits the files (the co-located form is in sample/android).

        The file is written as a `*Preview.kt` glob in any feature module, and named after the
        screen it renders by convention (`HomeScreenPreview.kt` for `HomeScreen`).

        This `@Preview` is the one from Compose Multiplatform's
        `org.jetbrains.compose.ui:ui-tooling-preview`. Its fully qualified annotation name is
        exactly the same as that of the Android-only `androidx.compose.ui:ui-tooling-preview`,
        so if IDE completion adds the latter, the iOS target can no longer be resolved.
        Previews can be written in `commonMain` because the former is used.
    """.trimIndent()
    allowedContents = """
        - Previews of stateless Composables that take state as arguments. It calls
          `HomeContent`, not `HomeScreen`, so it can be drawn without building a ViewModel
        - Several per target, one per state. Loading, loaded and failed can be viewed side by side
    """.trimIndent()
    forbiddenContents = """
        - `public` previews. Nothing else calls them, so make them `private`
        - Writing `AppTheme { }` directly in a preview. Wrapping is the job of `PreviewRoot`
        - Anything that touches a real Repository or the network. Write values as literals
        - Previews of components. They belong to the ComponentPreview role in `:ui`
    """.trimIndent()
    example("HomeScreenPreview", "The home screen, loading, loaded and failed")
    layout {
        "feature" / "*" / "commonMain".sourceSet / kotlin / "com/example/kmp/feature" / "*" /
            "*Preview".ktFile()
    }
    // Previews only, and private: nothing else calls them. They sit in the package of the screen
    // they preview, and draw it with `UiState` and `PreviewRoot` from `:ui`.
    ktFileContent {
        `package`(matchingDirectory)

        `import`("androidx.compose.**")
        `import`("com.example.kmp.ui.**")

        allow(annotated("Preview"), private, `fun`, "*Preview")
    }
}
