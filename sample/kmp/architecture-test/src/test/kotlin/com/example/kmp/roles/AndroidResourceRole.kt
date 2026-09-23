package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** What the Android build needs beside the Kotlin sources of `:app:android`. */
fun DeclarationContainerScope.androidResource() = "AndroidResource" {
    title = "Android リソース"
    summary = "AndroidManifest.xml、res/、proguard-rules.pro"
    example("AndroidManifest.xml", "アプリとモジュールのマニフェスト")
    example("res/values/strings.xml", "文字列リソース")
    // `res/*/` is the resource qualifier directory (`values`, `drawable`,
    // `mipmap-hdpi`, ...). Android decides those names, so the layout names the
    // level rather than each directory.
    layout {
        ":app:android".module {
            mainSourceSet / "AndroidManifest.xml".file()
            mainSourceSet / "res" / "*" / "*.xml".file()
        }
    }
}
