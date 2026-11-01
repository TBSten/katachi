package com.example.sample.app

import com.example.sample.app.activityEntrypoint
import com.example.sample.app.androidManifest
import com.example.sample.app.androidResource
import com.example.sample.app.applicationEntrypoint
import com.example.sample.app.proguardRules
import me.tbsten.katachi.dsl.DeclarationContainerScope

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

    activityEntrypoint()
    applicationEntrypoint()
    androidManifest()
    proguardRules()
    androidResource()
}
