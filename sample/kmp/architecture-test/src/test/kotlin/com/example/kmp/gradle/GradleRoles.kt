package com.example.kmp.gradle

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Build scripts. Not documented: they are part of the repository's shape but not part of the
 * architecture a reader of the docs needs.
 *
 * `documented = false` is written on the group and on each role, because katachi keeps the
 * declared value as written and does not inherit it from the parent.
 *
 * The package is `gradle` and not `build` on purpose: the repository's `.gitignore` has a
 * bare `build/` entry, which matches at every depth and would swallow the sources here. The
 * group keeps its original name.
 */
fun ArchitectureScope.gradleRoles() {
    "build".group(documented = false) {
        title = "ビルド"

        "GradleModule" {
            title = "モジュールのビルドスクリプト"
            // Covers `architecture-test/build.gradle.kts` as well: that module is a module
            // like any other, and the price of giving katachi a home of its own is that its
            // build script shows up here.
            summary = "各モジュールの build.gradle.kts"
            documented = false
            example("data/build.gradle.kts", ":data のビルドスクリプト")
            example("architecture-test/build.gradle.kts", ":architecture-test のビルドスクリプト")
            layout { }
        }
        "GradleRoot" {
            title = "ルートのビルドファイル"
            summary = "settings.gradle.kts、ルートの build.gradle.kts、gradle.properties、wrapper"
            documented = false
            example("settings.gradle.kts", "モジュール構成と catalog の宣言")
            layout { }
        }
    }
}
