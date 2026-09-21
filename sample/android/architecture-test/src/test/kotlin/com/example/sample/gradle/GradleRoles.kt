package com.example.sample.gradle

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the build setup: the Gradle scripts that describe how this project is assembled.
 *
 * The package is `gradle` and not `build` because `.gitignore` ignores `build/` at every
 * level, so a `build` package here would be invisible to git.
 *
 * Build files are checked but not documented: they are the same in every project and say
 * nothing about this app. `documented` is not inherited (a declared value is kept as
 * written), so each role below has to say `documented = false` for itself.
 *
 * Deliberately not `inline`. katachi reads the declaration site off the stack trace, and an
 * inlined frame reports a line number remapped past the end of the caller's file.
 */
fun ArchitectureScope.gradleRoles() {
    "build".group(documented = false) {
        title = "ビルド"

        "GradleModule" {
            title = "モジュールのビルドスクリプト"
            summary = "各モジュールの build.gradle.kts"
            documented = false
            layout { }
        }

        "GradleRoot" {
            title = "ルートのビルドスクリプト"
            summary = "settings.gradle.kts・ルートの build.gradle.kts・gradle.properties・wrapper"
            documented = false
            layout { }
        }
    }
}
