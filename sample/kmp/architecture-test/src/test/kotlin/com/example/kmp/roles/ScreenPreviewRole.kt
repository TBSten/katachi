@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The `@Preview` functions of a screen.
 *
 * Unlike sample/android, which keeps its `@Preview` functions in the same file as the
 * composable they render, this sample puts them in a `<Target>Preview.kt` file beside it.
 * Both shapes are common; having one sample of each is the point. A component's previews are
 * the neighboring [componentPreview] role.
 */
fun DeclarationContainerScope.screenPreview() = "ScreenPreview" {
    title = "Screen preview"
    summary = "A private @Composable annotated with @Preview for a screen. Placed in Home*Preview.kt in the feature " +
        "module that holds the screen, with the content wrapped in PreviewRoot"
    description = """
        A `private @Composable` annotated with `@Preview` that draws a screen. It is written in
        `<Target>Preview.kt` in the same package as the screen, inside the feature module that
        holds the screen. Keeping it in the same file as the target is also possible, but this
        sample splits the files (the co-located form is in sample/android).

        The file name is tied to the module the same way the screen it renders is: `:feature:home`
        may hold `Home*Preview.kt` and nothing else.

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
    example("HomeLoadedPreview", "The home screen, loaded")
    layout {
        ":feature:${capture("feature")}".module {
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcard("feature").pascalCase}*Preview".ktFile()
        }
    }
}
