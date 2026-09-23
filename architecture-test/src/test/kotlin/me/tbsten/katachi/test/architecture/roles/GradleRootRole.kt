package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktsFile

/** The role of the entry point of the whole build, plus the Gradle wrapper. */
fun DeclarationContainerScope.gradleRoot() = "GradleRoot" {
    title = "ルートのビルド"
    summary = "ビルド全体の入口と Gradle wrapper"
    example("settings.gradle.kts", "サブプロジェクトの宣言")
    example("gradle/libs.versions.toml", "Kotlin / kotest / konsist のバージョンの唯一の出どころ")
    layout {
        // The root project of this build. `":".module { }` resolves to the repository
        // root and injects exactly two things there: `"build".ignore()` and
        // `build.gradle.kts`. sample/kmp avoids this spelling because its root project
        // is not the repository root; here the two coincide, and the root project
        // carries nothing but the sample aggregation tasks, so it is safe to use.
        ":".module {
            "settings.gradle".ktsFile()
            "gradle.properties".file()
            "gradlew".file()
            "gradlew.bat".file()
            "gradle" {
                "libs.versions.toml".file()
                "wrapper" {
                    "gradle-wrapper.jar".file()
                    "gradle-wrapper.properties".file()
                }
            }
        }
    }
}
