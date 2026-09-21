package com.example.kmp.gradle

import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktsFile

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
            // One line per module, written the way `settings.gradle.kts` writes it. An empty
            // `.module { }` block is not an empty declaration: every module block says
            // `build/` is not checked and `build.gradle.kts` has to be there, which is the
            // whole of this role. The other roles say where that module's sources go.
            //
            // Only `:feature:*` is written with a wildcard, because that is the one place
            // where modules are expected to multiply. It stands for the feature modules that
            // exist, so a new one is picked up without this list being touched — and a
            // `feature/` directory that is not a module at all has nothing claiming it.
            layout {
                ":app:android".module { }
                ":architecture-test".module { }
                ":data".module { }
                ":feature:*".module { }
                ":navigation".module { }
                ":testing".module { }
                ":ui".module { }
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
        // No role for `build/`, `.kotlin/` or `local.properties`. They exist on a developer
        // machine and on CI but are in none of the commits, and `files` is left at its
        // default `gitTracked()`, so git is the one deciding which files this project has --
        // and it never reports an ignored file. Nothing has to be declared to keep them out.
    }
}
