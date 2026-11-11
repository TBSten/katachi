@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.feature

import com.example.sample.feature.featureSources
import com.example.sample.forbiddenContents
import com.example.sample.ui.navigation
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.camelCase
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.konsist.declarations.ktFileContent

/** The role of a feature module's only public entry: where the rest of the app navigates to. */
fun DeclarationContainerScope.route() = "Route".role {
    title = "Route"
    summary = "The destination of a screen. The only entrance a feature exposes to the outside"
    description = """
        The only thing a feature module shows to the outside. An object such as `HomeRoute`
        holds the destination path as `PATH`, and an extension function such as
        `NavGraphBuilder.homeScreen(...)` registers its own screen in the graph. The file name
        is `<Name>Route.kt`, where `<Name>` is the feature's name.

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
    // A new feature's Route, from the feature's name alone. Usually generated with its Screen and
    // ViewModel as the set `feature.NewFeature`; the callback it registers is `onNavigateUp`.
    //   ./gradlew :architecture-test:katachiTemplate --arg template=feature.Route --arg feature=profile
    layout {
        // `feature` is the module directory and the package directory alike, and the file name
        // follows it: feature/home/.../home/HomeRoute.kt, and nothing else named *Route.kt there.
        featureSources(capture("feature")) / "${capture("feature").pascalCase}Route".ktFile()
            .template {
                val feature = captureValue("feature")
                val pascal = feature.pascalCase
                // The same name the check derives from the file: SetsmokeRoute.kt -> setsmokeScreen.
                val register = "${feature.camelCase}Screen"
                """
                    |package com.example.sample.feature.$feature
                    |
                    |import androidx.navigation.NavGraphBuilder
                    |import androidx.navigation.compose.composable
                    |
                    |/** Navigation entry of the $feature feature: the only thing it publishes outside itself. */
                    |object ${pascal}Route {
                    |    const val PATH: String = "$feature"
                    |}
                    |
                    |/** Registers the $feature screen as a destination of the graph being built. */
                    |fun NavGraphBuilder.$register(onNavigateUp: () -> Unit) {
                    |    composable(${pascal}Route.PATH) {
                    |        ${pascal}Screen(onNavigateUp = onNavigateUp)
                    |    }
                    |}
                """.trimMargin()
            }
    }
    // `fileName` is a plain String: HomeRoute.kt holds `object HomeRoute` and `homeScreen(...)`.
    // Navigation is the only import, so another feature's Route cannot be reached from here.
    ktFileContent {
        `package`("com.example.sample.feature.${captureValue("feature")}")

        `import`("androidx.navigation.**")

        val feature = fileName.removeSuffix("Route").replaceFirstChar { it.lowercaseChar() }
        required(`object`, fileName) {
            required(const, `val`, "PATH").type("String")
        }
        required(`fun`, "${feature}Screen") // NavGraphBuilder.homeScreen(...)
    }
}
