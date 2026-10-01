package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The role of the manifest that declares the app to Android. */
fun DeclarationContainerScope.androidManifest() = "AndroidManifest" {
    title = "Android manifest"
    summary = "AndroidManifest.xml of :app, which declares the app to Android"
    description = """
        The `AndroidManifest.xml` of `:app`. Its shape is decided by the Android build system,
        not by this project. There is only one per app, so it is named exactly and a second one
        is a violation.

        `:app` is the only module with a manifest in this sample, so this role looks only at
        `:app`. If another module needs one, widen the role then.
    """.trimIndent()
    example("AndroidManifest.xml", "The app manifest")
    layout {
        "app" {
            mainSourceSet {
                "AndroidManifest.xml".file()
            }
        }
    }
}
