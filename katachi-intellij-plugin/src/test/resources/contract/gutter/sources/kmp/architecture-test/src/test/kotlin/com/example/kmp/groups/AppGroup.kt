package com.example.kmp.groups

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.roles.activityEntrypoint
import com.example.kmp.roles.androidManifest
import com.example.kmp.roles.androidResource
import com.example.kmp.roles.appRoot
import com.example.kmp.roles.xcodeProject
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The two applications: the Android one Gradle builds, and the iOS one Xcode builds.
 *
 * `:app:android` is the one nested module path of this sample, and it is also the one module
 * whose package does not follow it: the sources sit in `com.example.kmp.app`, not in
 * `com.example.kmp.app.android`. So the roles below write the package out as `com/example/kmp/app` —
 * a module that does not follow the rule should say so rather than bend it.
 */
fun DeclarationContainerScope.appGroup() = "app".group {
    title = "App"
    summary = "The Android app that Gradle builds and the iOS app that Xcode builds"
    description = """
        The two applications that actually ship. Both are "apps", but they are written so
        differently that they share one group only for that reason.

        `:app:android` is a Gradle module, so it is written with a module path. `app/ios` is
        deliberately not included by `settings.gradle.kts`, so no module path exists for it; it is
        declared as a plain directory key and `ignore()` stops the check there. A real KMP
        repository mixes directories Gradle manages with directories it does not, and this group
        shows that both can be written.

        `:app:android` has one more trait. It is the only nested module path in this sample, and
        also the only module whose package does not follow the module path (`com.example.kmp.app`,
        not `com.example.kmp.app.android`). That is why ActivityEntrypoint and AppRoot write the
        package out as `com/example/kmp/app`. Saying "this one is different" is kinder to
        the reader than bending the rule to fit.
    """.trimIndent()
    allowedContents = """
        - The entry point and the assembly of the whole app (`AppRoot`)
        - Resources the Android build requires
    """.trimIndent()
    forbiddenContents = """
        - The screens themselves. Screens live in the feature modules; this group only calls a Route
        - Logic meant to be shared. Anything written here is invisible to iOS
    """.trimIndent()

    activityEntrypoint()
    appRoot()
    androidManifest()
    androidResource()
    xcodeProject()
}
