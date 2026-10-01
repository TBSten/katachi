package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The composable that assembles the whole app, called by [activityEntrypoint]. */
fun DeclarationContainerScope.appRoot() = "AppRoot" {
    title = "App root"
    summary = "The whole-app @Composable that MainActivity calls via setContent, kept in :app:android"
    description = """
        The `@Composable` that assembles the whole app: the theme, the navigation bar, and the
        Route for the current destination. `MainActivity` (the ActivityEntrypoint role) only
        calls it, so everything that decides what the app looks like sits here.

        It is a role of its own because it is a different kind of file from the Activity: a
        `@Composable` that takes its dependencies as arguments and knows nothing about Android.
        That is what makes it the part that can be shared once `app/ios` gets a
        `ComposeUIViewController`.

        The package is written out (`com.example.kmp.app`) for the same reason as in
        ActivityEntrypoint: it does not follow the module path `:app:android`.
    """.trimIndent()
    allowedContents = """
        - The `@Composable` that assembles the whole app
    """.trimIndent()
    forbiddenContents = """
        - The screens themselves. Screens live in the feature modules; this role only calls a Route
        - Creating dependencies. `AppRoot` receives them as arguments; creating them is the Activity's job
    """.trimIndent()
    example("AppRoot", "The Composable that assembles the whole app")
    layout {
        "app/android" {
            mainSourceSet / kotlin / "com/example/kmp/app" / "AppRoot".ktFile()
        }
    }
}
