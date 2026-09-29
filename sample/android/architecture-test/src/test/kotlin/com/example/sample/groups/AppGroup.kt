package com.example.sample.groups

import com.example.sample.roles.androidResource
import com.example.sample.roles.entrypoint
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the application module itself: what `:app` holds beyond wiring the features
 * together.
 *
 * `:app` is the one module of the app whose package is not derived from its module path:
 * its sources sit directly in `com.example.sample`, so the package is written out as a key
 * instead of with `modulePackage`. Everything else about the module — where it is, that it
 * has a build script, that its `build/` is not checked — still comes from `":app".module`.
 */
fun DeclarationContainerScope.appGroup() = "app".group {
    title = "Entrypoint layer"
    summary = "What :app holds: the launch entrypoints and the Android resources"
    description = """
        The `:app` module itself. It is the only module in this app that may know every feature:
        `MainActivity` builds the navigation graph and connects `:feature:home` and
        `:feature:settings`.

        It gathers two things that can only live in `:app`. The types Android touches at launch
        (`MainActivity` / `MainApplication`), and the non-Kotlin files an application needs
        (`AndroidManifest.xml` / `res/` / `proguard-rules.pro`). Both come from "being the
        application", not from any feature of the app.

        Screen contents are not here. `:app` sits at the top of the dependency graph and only
        refers downward, so replacing `:app` as a whole does not break the modules below it. If
        Composables start piling up here, they should move into a feature module.

        `:app` is one of the two modules whose package cannot be derived from the module path,
        so it writes `com/example/sample` directly. It is the application itself and has no
        mapping like `:ui` to `com.example.sample.ui` (the other one is `:architecture-test`).
    """.trimIndent()

    entrypoint()
    androidResource()
}
