package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of moving between screens, which belongs to no single feature. */
fun DeclarationContainerScope.navigation() = "Navigation" {
    title = "Screen navigation"
    summary = "Movement between screens, kept in :navigation"
    description = """
        The entry point for moving between screens. The `AppNavigator` interface exposes only
        `navigateTo(destination)` and `navigateUp()`, and `rememberAppNavigator(navController)`
        returns an implementation wrapping `NavHostController`. The implementation class is
        private, and its name is not even visible from outside.

        `:feature:*` depends only on this interface. If a feature could hold a
        `NavHostController` directly, any feature could rewrite the whole graph, so what it can
        touch is narrowed to these two methods. It is a separate module from `:ui` for the same
        reason: code that just wants screen parts should not pull in the navigation dependency.

        The graph itself is not here. Only `AppNavHost` in `:app` knows which Routes are lined
        up and how, and `:navigation` holds only the "means of moving".

        File names are `*.kt` (in the package directly under the module). Types related to the
        means of navigation go here, but the destination of a particular screen (such as
        `HomeRoute`) belongs to the Route role on the feature side.
    """.trimIndent()
    example("AppNavigator", "The entry point for screen navigation")
    layout {
        "navigation" {
            mainSourceSet / kotlin / "com/example/sample/navigation" / "*".ktFile()
        }
    }
}
