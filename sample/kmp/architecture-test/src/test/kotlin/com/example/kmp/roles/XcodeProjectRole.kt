package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The iOS application, which Xcode builds and Gradle knows nothing about.
 *
 * `app/ios` is not a Gradle module, so it is not written as one: there is no module path that
 * resolves to it and no `build.gradle.kts` to require. It stays a plain directory key, which
 * is the whole point of having it in this sample. Xcode owns what is inside it, so the role
 * declares the directory and stops the check there. It is still a declared directory with a
 * role and a summary around it, which is the only way katachi lets anything go unchecked.
 */
fun DeclarationContainerScope.xcodeProject() = "XcodeProject" {
    title = "Xcode project"
    summary = "Under app/ios. Outside Gradle's management and not checked"
    description = """
        The iOS app side. `app/ios` is not a Gradle module. `settings.gradle.kts` deliberately
        does not include it, so it cannot be written with a module path and cannot be required to
        have a `build.gradle.kts`. It is declared as a plain directory key and `ignore()` stops
        the check inside it.

        Declaring it while not checking it is the showcase of this role. The only way to say
        "do not look here" in katachi is to declare the directory as a role with a summary; if
        nothing is written, everything under `app/ios` becomes "files no role claims".

        Inside are the Swift sources and `Info.plist`, that is, only the shape of an iOS app.
        `iosApp.xcodeproj/` is not committed. A handwritten `project.pbxproj` breaks in ways
        Xcode cannot open, and since this sample does not build for iOS, it would be dead weight
        anyway. Create one in Xcode if you want to run it.
    """.trimIndent()
    allowedContents = """
        - What Xcode owns: Swift sources, `Info.plist` and the asset catalog
    """.trimIndent()
    forbiddenContents = """
        - Kotlin code. Put code to be shared in a KMP module such as `:data` and hand it over
          as a framework (this sample does not set that up)
    """.trimIndent()
    example("iosAppApp.swift", "The SwiftUI entry point")
    example("ContentView.swift", "The screen on the iOS side")
    layout {
        "app/ios".ignore()
    }
}
