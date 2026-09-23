package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktsFile

/** The role of the build's root: what makes the modules above into one Gradle build. */
fun DeclarationContainerScope.gradleRoot() = "GradleRoot" {
    title = "ルートのビルドスクリプト"
    summary = "settings.gradle.kts・ルートの build.gradle.kts・gradle.properties・wrapper"
    documented = false
    layout {
        // `":"` is the root project, which resolves to the project root itself, so
        // its build script and its `build/` come from the same sugar as every other
        // module's and only what is peculiar to the root is written out.
        ":".module {
            "settings.gradle".ktsFile()
            "gradle.properties".file()
            "gradlew".file()
            "gradlew.bat".file()
            "gradle" {
                "sample.versions.toml".file()
                "wrapper" {
                    "gradle-wrapper.jar".file()
                    "gradle-wrapper.properties".file()
                }
            }
        }
    }
}
