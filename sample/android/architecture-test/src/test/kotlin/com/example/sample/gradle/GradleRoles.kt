package com.example.sample.gradle

import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutScope

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
 * The build's *output* is not declared here at all. `build/`, `.kotlin/` and
 * `local.properties` are all ignored by `.gitignore`, and the default `files = gitTracked()`
 * never offers them to the check, so there is nothing for a role to cover.
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
            layout {
                // One line per `include(...)` in settings.gradle.kts, in the same order.
                // Step 3 writes each of these as `":app".module { }`, which expands to
                // exactly the directory and the build script below.
                gradleModule("app")
                gradleModule("architecture-test")
                gradleModule("feature/home")
                gradleModule("feature/settings")
                gradleModule("data")
                gradleModule("ui")
                gradleModule("navigation")
                gradleModule("testing")
            }
        }

        "GradleRoot" {
            title = "ルートのビルドスクリプト"
            summary = "settings.gradle.kts・ルートの build.gradle.kts・gradle.properties・wrapper"
            documented = false
            layout {
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
            }
        }
    }
}

/**
 * A Gradle module: its directory and its build script.
 *
 * Its `build/` needs no mention. `.gitignore` covers `build/` at every level, so under the
 * default `files = gitTracked()` the check is never offered it.
 *
 * Step 3 deletes this function: `":feature:home".module { }` is defined as exactly the two
 * declarations below, and the check result has to stay identical across the rewrite.
 */
private fun LayoutScope.gradleModule(directory: String): LayoutDirectory =
    directory {
        "build.gradle".ktsFile()
    }
