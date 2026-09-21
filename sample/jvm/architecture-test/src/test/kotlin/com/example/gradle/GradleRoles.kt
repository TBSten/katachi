package com.example.gradle

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the build definition.
 *
 * Build files are checked like everything else, but they are noise in the generated
 * documentation, so the whole group opts out (D4: `documented` is an argument on a group,
 * a property inside a role block).
 *
 * What the build *writes* needs no role at all: `build/` and `.kotlin/` are listed in
 * `.gitignore`, and the default `files = gitTracked()` never offers them to the check.
 */
fun ArchitectureScope.gradleRoles() {
    "build".group(documented = false) {
        title = "ビルド"

        "Gradle" {
            title = "Gradle スクリプト"
            summary = "ビルドの定義と Gradle wrapper"
            example("build.gradle.kts", "このサンプルのビルド定義")
            example("architecture-test/build.gradle.kts", "アーキテクチャ定義モジュールのビルド定義")
            example("settings.gradle.kts", "composite build と version catalog の配線")
            layout {
                // The application module is the root project itself, so its build files sit
                // directly under `layout { }`, which is the repository root.
                "settings.gradle".ktsFile()
                "build.gradle".ktsFile()
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
                "architecture-test" / "build.gradle".ktsFile()
            }
        }
    }
}
