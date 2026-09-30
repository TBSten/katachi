@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.featureSources
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/** The role of a feature module's only public entry: where the rest of the app navigates to. */
fun DeclarationContainerScope.route() = "Route" {
    title = "Route"
    summary = "The destination of a screen. The only entrance a feature exposes to the outside"
    description = """
        The only thing a feature module shows to the outside. An object such as `HomeRoute`
        holds the destination path as `PATH`, and an extension function such as
        `NavGraphBuilder.homeScreen(...)` registers its own screen in the graph. The file name
        is `<Name>Route.kt`, decided by the module name.

        Only `AppNavHost` in `:app` builds the graph, and what `:app` touches is limited to what
        this role exposes, such as `HomeRoute.PATH` and `homeScreen(...)`. Neither `HomeScreen`
        nor `HomeViewModel` is called from `:app`. Even as features grow, one line is added to
        `:app`.

        Navigation out of a screen is written by passing the callbacks received as arguments of
        this extension function (`onNavigateToSettings` / `onNavigateUp`) to the Screen. `:app`
        decides which screen to go to, and a feature never imports another feature's Route.
    """.trimIndent()
    forbiddenContents = """
        - UI. Drawing as a `@Composable` is the Screen's job; here is only the registration with `composable(...)`
        - References to another feature's Route
        - Logic beyond assembling arguments. Navigation decisions belong to the caller
    """.trimIndent()
    example("HomeRoute", "The destination of the home screen")
    example("SettingsRoute", "The destination of the settings screen")
    layout {
        ":feature:${capture("feature")}".module {
            featureSources() / "${wildcard("feature").pascalCase}Route".ktFile()
        }
    }
}
