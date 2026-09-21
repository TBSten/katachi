package com.example.gradle

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the build definition.
 *
 * Build files are checked like everything else, but they are noise in the generated
 * documentation, so the whole group opts out (D4: `documented` is an argument on a group,
 * a property inside a role block).
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
            layout { }
        }
    }
}
