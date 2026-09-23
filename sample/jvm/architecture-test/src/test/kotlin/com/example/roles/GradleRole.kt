package com.example.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktsFile

/** The role of the build definition and the Gradle wrapper. */
fun DeclarationContainerScope.gradle() = "Gradle" {
    title = "Gradle スクリプト"
    summary = "ビルドの定義と Gradle wrapper"
    example("build.gradle.kts", "このサンプルのビルド定義")
    example("architecture-test/build.gradle.kts", "アーキテクチャ定義モジュールのビルド定義")
    example("settings.gradle.kts", "composite build と version catalog の配線")
    layout {
        // The application module is the root project, so `":"` resolves to the
        // repository root and everything below reads as a plain root-relative path.
        // `build.gradle.kts` is not written out: `.module { }` is exactly a directory
        // plus `"build".ignore()` and `"build.gradle".ktsFile()`, so writing it again
        // would say the same thing twice.
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
        // The only thing this role has to say about `:architecture-test` is what
        // every module brings with it, so the block is empty. Where its sources may
        // live is `testing/ArchitectureDefinition`'s business.
        ":architecture-test".module { }
    }
}
