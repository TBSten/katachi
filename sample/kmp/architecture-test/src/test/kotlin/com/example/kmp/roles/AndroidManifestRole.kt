package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The role of the manifest that declares the app to Android. */
fun DeclarationContainerScope.androidManifest() = "AndroidManifest" {
    title = "Android manifest"
    summary = "AndroidManifest.xml of :app:android, which declares the app to Android"
    description = """
        The `AndroidManifest.xml` of `:app:android`. Its shape is decided by the Android build
        system, not by this project. There is only one per app, so it is named exactly and a
        second one is a violation.

        `:app:android` is the only module with a manifest in this sample, so this role looks
        only at it. If another module needs one, widen the role then.
    """.trimIndent()
    example("AndroidManifest.xml", "The app manifest")
    layout {
        "app/android" {
            mainSourceSet / "AndroidManifest.xml".file()
        }
    }
}
