package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The role of everything `:app` carries that is not Kotlin. */
fun DeclarationContainerScope.androidResource() = "AndroidResource" {
    title = "Android resources"
    summary = "AndroidManifest.xml, res/ and proguard-rules.pro"
    description = """
        What `:app` carries that is not Kotlin: `AndroidManifest.xml`, `res/` and
        `proguard-rules.pro`. The shape of all three is decided by the Android build system, not
        by this project.

        `AndroidManifest.xml` and `proguard-rules.pro` are named exactly. There is only one of
        each per app, so a second one is a violation.

        `res/` is `ignore()`d. Its inner structure (`values/`, `drawable-*/` and so on) follows
        rules set by the Android resource system, which AGP already validates. Writing it down
        again in katachi would make a second copy of the same rules, and one of the two would
        always go stale. Right now it holds `values/strings.xml` and `values/themes.xml`.


        `:app` is not the only module that can hold resources (`:ui` is an Android library too),
        but no module other than `:app` has a `res/` in this sample, so this role looks only at
        `:app`. If resources are placed in another module, widen the role then.
    """.trimIndent()
    example("AndroidManifest.xml", "The app manifest")
    example("res/values/strings.xml", "String resources")
    layout {
        ":app".module {
            "proguard-rules.pro".file()
            mainSourceSet {
                "AndroidManifest.xml".file()
                // `ignore()` rather than a tree of directories: the shape of `res/`
                // is the Android resource system's, not this project's, and it is
                // already validated by AGP.
                "res".ignore()
            }
        }
    }
}
