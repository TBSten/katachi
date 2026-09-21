package com.example.sample.application

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the application module itself: what `:app` holds beyond wiring the features
 * together.
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
                // Both named exactly, so an app that loses its entry point fails with
                // `[MissingFile]` instead of quietly passing.
                "app/src/main/kotlin/com/example/sample" {
                    "MainActivity".ktFile()
                    "MainApplication".ktFile()
                }
            }
        }

        "AndroidResource" {
            title = "Android リソース"
            summary = "AndroidManifest.xml・res/・proguard-rules.pro"
            example("AndroidManifest.xml", "アプリの構成")
            example("res/values/strings.xml", "文字列リソース")
            layout {
                "app" {
                    "proguard-rules.pro".file()
                    "src/main" {
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
