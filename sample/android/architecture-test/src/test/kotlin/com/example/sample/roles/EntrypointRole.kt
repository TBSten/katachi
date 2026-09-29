package com.example.sample.roles

import com.example.sample.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role Android itself reaches for when it starts the app. */
fun DeclarationContainerScope.entrypoint() = "Entrypoint" {
    title = "Entrypoint"
    summary = "The types Android touches when it launches the app, kept in :app"
    description = """
        The types Android touches first at launch. `:app` has one `MainActivity` and one
        `MainApplication`, named exactly. If either is gone, the check fails with
        `[MissingFile]`, so that an app that cannot launch never passes.

        `MainActivity` contains only `setContent { AppTheme { AppNavHost() } }`. `AppNavHost` is
        a private `@Composable` in the same file that lines up the Routes exposed by
        `:feature:*` and builds the navigation graph. `:app` is the only module allowed to know
        every feature, and that knowledge stays inside this file.

        `MainApplication` only extends `Application`. It is left empty as the place to add "once
        at launch" work such as initializing a DI container.

        This role writes its package directly as `com/example/sample` instead of using
        `modulePackage`. `:app` is the application itself and has no mapping to the module path
        like `:ui` to `com.example.sample.ui`.
    """.trimIndent()
    forbiddenContents = """
        Screen contents. `:app` only connects features; the UI lives in `:ui` and `:feature:*`.
        If Composables start piling up here, they should move into a feature module.
    """.trimIndent()
    example("MainActivity", "The Activity shown at launch")
    example("MainApplication", "The Application implementation")
    layout {
        ":app".module {
            // Both named exactly, so an app that loses its entry point fails with
            // `[MissingFile]` instead of quietly passing.
            mainSourceSet / kotlin / "com/example/sample" {
                "MainActivity".ktFile()
                "MainApplication".ktFile()
            }
        }
    }
}
