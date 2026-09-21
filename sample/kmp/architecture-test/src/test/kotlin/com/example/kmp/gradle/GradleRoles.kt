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
            // One line per module directory. `settings.gradle.kts` is the real list; keeping
            // the two in step by hand is exactly the cost step 3 removes by resolving the
            // module paths from the build itself.
            //
            // Only `feature/*` is written with a wildcard, because that is the one place
            // where modules are expected to multiply. A wildcard also makes the declaration
            // optional, so the seven spelled-out scripts are the ones a deletion would
            // report as `[MissingFile]`.
            layout {
                "app/android/build.gradle".ktsFile()
                "architecture-test/build.gradle".ktsFile()
                "data/build.gradle".ktsFile()
                "feature" / "*" / "build.gradle".ktsFile()
                "navigation/build.gradle".ktsFile()
                "testing/build.gradle".ktsFile()
                "ui/build.gradle".ktsFile()
            }
        }
        "GradleRoot" {
            title = "ルートのビルドファイル"
            summary = "settings.gradle.kts、ルートの build.gradle.kts、gradle.properties、wrapper"
            documented = false
            example("settings.gradle.kts", "モジュール構成と catalog の宣言")
            // Directly under `layout { }` the paths are relative to the project root, which
            // for this sample is `sample/kmp` -- the first directory above the test's working
            // directory holding a `gradlew` (see ProjectRootSpec).
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
        // No role for `build/`, `.kotlin/` or `local.properties`. They exist on a developer
        // machine and on CI but are in none of the commits, and `files` is left at its
        // default `gitTracked()`, so git is the one deciding which files this project has --
        // and it never reports an ignored file. Nothing has to be declared to keep them out.
    }
}
