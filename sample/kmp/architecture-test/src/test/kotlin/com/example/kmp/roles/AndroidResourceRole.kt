package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** What the Android build needs beside the Kotlin sources of `:app:android`. */
fun DeclarationContainerScope.androidResource() = "AndroidResource" {
    title = "Android resources"
    summary = "AndroidManifest.xml and the resource XML under res/. Only :app:android has them"
    description = """
        What the Android build requires besides Kotlin sources: `AndroidManifest.xml` and the
        resource XML under `res/`. In this sample only `:app:android` has them.

        The `*` in `res/*/` is a resource-qualifier directory (`values`, `drawable`,
        `mipmap-hdpi` ...). Android, not this project, decides which names can exist, so only the
        hierarchy is declared and individual directory names are not written.

        Library modules (such as `:ui`) have no resources. Colors and spacing are written in
        Kotlin in the theme package of `:ui`, and nothing corresponds to `res/values/`. Not using
        an Android-only place is what makes them shareable with iOS.
    """.trimIndent()
    forbiddenContents = """
        - Kotlin code. `src/main/kotlin` is the Entrypoint role
        - iOS resources. What is inside `app/ios` is Xcode's territory
        - Text shown on screens. In KMP, iOS cannot read `res/`, so a string meant to be shared
          and written in `strings.xml` becomes usable only on Android. This sample writes even the
          destination labels and the button text directly on the Compose side (`commonMain`), and
          `strings.xml` holds only the app name
    """.trimIndent()
    example("AndroidManifest.xml", "The app manifest")
    example("res/values/strings.xml", "String resources")
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
