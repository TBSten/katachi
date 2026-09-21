package com.example.sample.application

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the application module itself: what `:app` holds beyond wiring the features
 * together.
 *
 * `:app` is the one module of the app whose package is not derived from its module path:
 * its sources sit directly in `com.example.sample`, so the package is written out as a key
 * instead of with `modulePackage`. Everything else about the module — where it is, that it
 * has a build script, that its `build/` is not checked — still comes from `":app".module`.
 *
 * Deliberately not `inline`, for the reason spelled out on [uiRoles].
 */
fun ArchitectureScope.appRoles() {
    "app".group {
        title = "エントリーポイントレイヤー"

        "Entrypoint" {
            title = "エントリポイント"
            summary = ":app に置く、Android がアプリを起動するときに触る型"
            example("MainActivity", "起動時に表示される Activity")
            example("MainApplication", "Application の実装")
            layout {
                ":app".module {
                    // Both named exactly, so an app that loses its entry point fails with
                    // `[MissingFile]` instead of quietly passing.
                    mainSourceSet / kotlin / "com/example/sample" {
                        "MainActivity".ktFile()
                        "MainApplication".ktFile()
                    }
                }
            }
        }

        "AndroidResource" {
            title = "Android リソース"
            summary = "AndroidManifest.xml・res/・proguard-rules.pro"
            example("AndroidManifest.xml", "アプリの構成")
            example("res/values/strings.xml", "文字列リソース")
            layout {
                ":app".module {
                    "proguard-rules.pro".file()
                    mainSourceSet {
                        "AndroidManifest.xml".file()
                        // `ignore()` rather than a tree of directories: the shape of `res/`
                        // is the Android resource system's, not this project's, and it is
                        // already validated by AGP.
                        "res".ignore()
                    }
                }
            }
        }
    }
}
