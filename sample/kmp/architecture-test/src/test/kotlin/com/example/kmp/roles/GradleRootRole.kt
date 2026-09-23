package com.example.kmp.roles

import com.example.kmp.processor.owner
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.kotlin.ktsFile

/** The build files that sit around the modules rather than inside one of them. */
fun DeclarationContainerScope.gradleRoot() = "GradleRoot" {
    title = "ルートのビルドファイル"
    summary = "settings.gradle.kts、ルートの build.gradle.kts、gradle.properties、wrapper"
    documented = false
    owner = "platform"
    example("settings.gradle.kts", "モジュール構成と catalog の宣言")
    // Directly under `layout { }` the paths are relative to the project root, which
    // for this sample is `sample/kmp` -- the first directory above the test's working
    // directory holding a `gradlew` (see ProjectRootSpec).
    //
    // Not written as `":".module { }` even though the root project is a Gradle module
    // too: a module block's container is the project root itself there, so its
    // `build/` line would stop the check over the whole repository. What this role
    // describes is the files around the build, not a module.
    layout {
        "settings.gradle".ktsFile()
        "build.gradle".ktsFile()
        "gradle.properties".file()
        "gradlew".file()
        "gradlew.bat".file()
        "gradle" {
            // Not `libs.versions.toml`: this sample reads the repository root catalog
            // as `libs` and keeps only what it adds on top in a file of its own.
            "sample.versions.toml".file()
            "wrapper" {
                "gradle-wrapper.jar".file()
                "gradle-wrapper.properties".file()
            }
        }
    }
}
