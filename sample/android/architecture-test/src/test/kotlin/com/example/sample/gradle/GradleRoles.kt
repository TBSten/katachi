package com.example.sample.gradle

import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktsFile

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
 * `GradleModule` is where the module paths are listed and nothing else is: `".module { }"`
 * is defined as a directory plus `"build".ignore()` plus `"build.gradle".ktsFile()`, so an
 * empty block is already the whole of what a Gradle module is from this role's point of
 * view. The other roles of the definition name the same modules again to say what is
 * *inside* them.
 *
 * The build's *output* needs no declaration of its own. `build/` is ignored by `.gitignore`
 * and the default `files = gitTracked()` never offers it to the check; the `build` that
 * `.module { }` adds is what makes the definition say so out loud rather than rely on it.
 *
 * Deliberately not `inline`. katachi reads the declaration site off the stack trace, and an
 * inlined frame reports a line number remapped past the end of the caller's file.
 */
fun ArchitectureScope.gradleRoles() {
    "build".group {
        documented = false
        title = "ビルド"

        "GradleModule" {
            title = "モジュールのビルドスクリプト"
            summary = "各モジュールの build.gradle.kts"
            documented = false
            layout {
                // One line per `include(...)` in settings.gradle.kts, in the same order —
                // except the features, which are one `":feature:*"` because a feature module
                // is added without asking anyone.
                ":app".module { }
                ":architecture-test".module { }
                ":feature:*".module { }
                ":data".module { }
                ":ui".module { }
                ":navigation".module { }
                ":testing".module { }
            }
        }

        "GradleRoot" {
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
    }
}
