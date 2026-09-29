package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** Where the Android application starts, and the composable it hands the whole screen to. */
fun DeclarationContainerScope.entrypoint() = "Entrypoint" {
    title = "Entrypoint"
    summary = "The starting point of the Android app: a ComponentActivity and the whole-app @Composable it calls via setContent"
    description = """
        Where the Android app starts running. It goes in `src/main` of `:app:android`, not in the
        `commonMain` of a KMP module. `:app:android` is an Android application module, so its
        sources sit in `src/main` like any other Android module.

        The content has two levels. `MainActivity` is wiring only: it creates
        `UserRepositoryImpl` and calls `setContent { AppRoot(...) }`. Assembling the theme, the
        navigation bar and the Route for the current destination is `AppRoot`'s job. They are
        split because `AppRoot` is the part that can be shared once `app/ios` gets a
        `ComposeUIViewController`. The Activity stays Android-specific.

        The package of this module is `com.example.kmp.app`, which cannot be derived from the
        module path `:app:android`. That is why the layout writes the package out instead of
        using `modulePackage`. When something breaks the rule, it is more honest to say so.
    """.trimIndent()
    allowedContents = """
        - The platform entry point (`ComponentActivity`) and its minimal wiring
        - The `@Composable` that assembles the whole app
    """.trimIndent()
    forbiddenContents = """
        - The screens themselves. Screens live in the feature modules; this role only calls a Route
        - State. Screen state belongs to the ViewModel and the current location to `Navigator` in `:navigation`
        - Parts meant to be shared. Anything written here is invisible to iOS
    """.trimIndent()
    example("MainActivity", "The Activity shown at launch")
    example("AppRoot", "The Composable that assembles the whole app")
    // `mainSourceSet`, not `"commonMain".sourceSet`: `:app:android` is the Android
    // application module, and its code lives in `src/main` like any Android module's.
    layout {
        ":app:android".module {
            mainSourceSet / kotlin / "com/example/kmp/app" / "*".ktFile()
        }
    }
}
