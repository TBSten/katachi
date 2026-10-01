package com.example.sample.roles

import com.example.sample.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the Activity Android starts the app with. */
fun DeclarationContainerScope.activityEntrypoint() = "ActivityEntrypoint" {
    title = "Activity entrypoint"
    summary = "The single Activity Android launches, kept in :app"
    description = """
        The `Activity` Android touches first at launch. `:app` has one `MainActivity`, named
        exactly. If it is gone, the check fails with `[MissingFile]`, so that an app that cannot
        launch never passes.

        `MainActivity` contains only `setContent { AppTheme { AppNavHost() } }`. `AppNavHost` is
        a private `@Composable` in the same file that lines up the Routes exposed by
        `:feature:*` and builds the navigation graph. `:app` is the only module allowed to know
        every feature, and that knowledge stays inside this file.

        This role writes the package `com/example/sample` directly. `:app` is the application
        itself and has no mapping to the module path like `:ui` to `com.example.sample.ui`.
    """.trimIndent()
    forbiddenContents = """
        Screen contents. `:app` only connects features; the UI lives in `:ui` and `:feature:*`.
        If Composables start piling up here, they should move into a feature module.
    """.trimIndent()
    example("MainActivity", "The Activity shown at launch")
    layout {
        "app" {
            // Named exactly, so an app that loses its entry point fails with
            // `[MissingFile]` instead of quietly passing.
            mainSourceSet / kotlin / "com/example/sample" {
                "MainActivity".ktFile()
            }
        }
    }
}
