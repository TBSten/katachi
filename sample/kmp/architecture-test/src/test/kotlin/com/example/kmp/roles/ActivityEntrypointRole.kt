package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** Where the Android application starts: the one Activity it launches. */
fun DeclarationContainerScope.activityEntrypoint() = "ActivityEntrypoint" {
    title = "Activity entrypoint"
    summary = "The starting point of the Android app: the ComponentActivity that calls setContent, kept in :app:android"
    description = """
        Where the Android app starts running. It goes in `src/main` of `:app:android`, not in the
        `commonMain` of a KMP module. `:app:android` is an Android application module, so its
        sources sit in `src/main` like any other Android module.

        `MainActivity` is wiring only: it creates `UserRepositoryImpl` and calls
        `setContent { AppRoot(...) }`. It is named exactly, so an app that loses its entry point
        fails with `[MissingFile]` instead of quietly passing. Assembling the theme, the
        navigation bar and the Route for the current destination is the AppRoot role's job; the
        two are split because `AppRoot` is the part that can be shared once `app/ios` gets a
        `ComposeUIViewController`, while the Activity stays Android-specific.

        The package of this module is `com.example.kmp.app`, which does not follow the
        module path `:app:android`, so the layout writes the package out as `com/example/kmp/app`
        rather than as `com/example/kmp/app/android`. When something breaks the rule, it is more
        honest to say so.
    """.trimIndent()
    allowedContents = """
        - The platform entry point (`ComponentActivity`) and its minimal wiring
    """.trimIndent()
    forbiddenContents = """
        - The screens themselves. Screens live in the feature modules; this role only starts the app
        - State. Screen state belongs to the ViewModel and the current location to `Navigator` in `:navigation`
        - Parts meant to be shared. Anything written here is invisible to iOS
    """.trimIndent()
    example("MainActivity", "The Activity shown at launch")
    // `mainSourceSet`, not `"commonMain".sourceSet`: `:app:android` is the Android
    // application module, and its code lives in `src/main` like any Android module's.
    layout {
        "app/android" {
            mainSourceSet / kotlin / "com/example/kmp/app" / "MainActivity".ktFile()
        }
    }
}
