package com.example.kmp.groups

import com.example.kmp.roles.androidResource
import com.example.kmp.roles.entrypoint
import com.example.kmp.roles.xcodeProject
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The two applications: the Android one Gradle builds, and the iOS one Xcode builds.
 *
 * `:app:android` is the one nested module path of this sample, and it is also the one module
 * whose package does not follow it: the sources sit in `com.example.kmp.app`, not in
 * `com.example.kmp.app.android`. So the package is written out in the roles below instead of
 * coming from `modulePackage` — a module that does not follow the rule should say so rather
 * than bend it.
 */
fun DeclarationContainerScope.appGroup() = "app".group {
    title = "アプリ"

    entrypoint()
    androidResource()
    xcodeProject()
}
