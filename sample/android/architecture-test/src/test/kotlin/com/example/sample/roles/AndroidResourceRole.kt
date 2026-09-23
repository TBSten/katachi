package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The role of everything `:app` carries that is not Kotlin. */
fun DeclarationContainerScope.androidResource() = "AndroidResource" {
    title = "Android リソース"
    summary = "AndroidManifest.xml・res/・proguard-rules.pro"
    example("AndroidManifest.xml", "アプリの構成")
    example("res/values/strings.xml", "文字列リソース")
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
