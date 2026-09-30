@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * What binds a screen to a navigation destination, named after its feature module.
 *
 * The third of the three files a feature module is required to hold, and the third place
 * `wildcard("feature")` ties a file name to the module it sits in.
 */
fun DeclarationContainerScope.route() = "Route" {
    title = "Route"
    summary = "Ties a screen to a navigation Destination and also takes on creating the ViewModel"
    description = """
        The only entry reachable from outside a feature module. `object HomeRoute` has
        `destination` (which `Destination` this screen is) and `Content()` (the call that draws
        it), and the caller knows only those two.

        The key point is that this role takes on creating the ViewModel. `Content()` calls
        `viewModel { HomeViewModel(repository) }`, so the AppRoot of `:app:android` can show the
        screen without knowing that the type HomeViewModel exists. Dependencies (currently
        UserRepository) are received as arguments. This sample has no DI container and hands
        them over by hand.

        It is fixed to `commonMain` so that the same Route can be called from the Android
        `AppRoot` and, later, from `app/ios` once it has a `ComposeUIViewController`.
    """.trimIndent()
    forbiddenContents = """
        - The content of the screen. Building the Compose tree is the Screen's job
        - The definition of the destination itself. `Destination` is in `:navigation`, and the Route only points at it
        - Control of navigation. Holding where we are now is the job of `Navigator` in `:navigation`
    """.trimIndent()
    example("HomeRoute", "The destination of the home screen")
    example("SettingsRoute", "The destination of the settings screen")
    layout {
        ":feature:${capture("feature")}".module {
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcard("feature").pascalCase}Route".ktFile()
        }
    }
}
