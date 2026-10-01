package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The whole of the `:navigation` module: where a screen can be reached from, and from where. */
fun DeclarationContainerScope.navigation() = "Navigation" {
    title = "Navigation"
    summary = "The definition of destinations, and the Navigator that holds the current location"
    description = """
        The whole `:navigation` module is one role. No packages are cut, so the module package
        directly under `commonMain` is the whole scope. `Destination` is the list of destinations
        and `Navigator` holds the current location as a `StateFlow`.

        It does not depend on Compose. The build script does not apply the Compose plugin, so a
        `@Composable` written here would not compile. How to show the destinations (how the
        navigation bar is laid out) is the job of `AppRoot` in `:app:android`; this role holds
        only "what places exist" and "where we are now".

        Keeping its own navigation instead of adding a navigation library is intentional. A
        library would add one more place, the graph definition, and blur the "what goes where"
        that this sample wants to show.
    """.trimIndent()
    allowedContents = """
        - The type of destinations, their list (`Destination.topLevel`) and lookup from a route string
        - Holding the current location and a way to change it
    """.trimIndent()
    forbiddenContents = """
        - `@Composable` and the screens themselves
        - Dependencies on feature modules. The dependency points the other way: the Route of each
          feature reads `:navigation`. If this module knew the features, it would grow with every
          screen added
    """.trimIndent()
    example("Destination", "The list of destinations")
    example("Navigator", "The type that holds the current destination")
    layout {
        "navigation" {
            "commonMain".sourceSet / kotlin / "com/example/kmp/navigation" / "*".ktFile()
        }
    }
}
