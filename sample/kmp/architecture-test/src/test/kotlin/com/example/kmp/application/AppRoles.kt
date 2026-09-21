package com.example.kmp.application

import me.tbsten.katachi.dsl.ArchitectureScope

/** The two applications: the Android one Gradle builds, and the iOS one Xcode builds. */
fun ArchitectureScope.appRoles() {
    "app".group {
        title = "アプリ"

        "Entrypoint" {
            title = "エントリポイント"
            summary = "Android アプリの起動点。ComponentActivity と、そこから setContent で呼ぶアプリ全体の @Composable"
            example("MainActivity", "起動時に表示される Activity")
            example("AppRoot", "テーマとナビゲーションを組み立てる Composable")
            layout {
                "app/android/src/main/kotlin" {
                    "com/example/kmp/app" / "*".ktFile()
                }
            }
        }
        "AndroidResource" {
            title = "Android リソース"
            summary = "AndroidManifest.xml、res/、proguard-rules.pro"
            example("AndroidManifest.xml", "アプリとモジュールのマニフェスト")
            example("res/values/strings.xml", "文字列リソース")
            // `res/*/` is the resource qualifier directory (`values`, `drawable`,
            // `mipmap-hdpi`, ...). Android decides those names, so the layout names the
            // level rather than each directory.
            layout {
                "app/android/src/main" {
                    "AndroidManifest.xml".file()
                    "res" / "*" / "*.xml".file()
                }
            }
        }
        // `app/ios` is not a Gradle module, and Xcode owns what is inside it, so the role
        // declares the directory and stops the check there. It is still a declared
        // directory with a role and a summary around it, which is the only way katachi lets
        // anything go unchecked.
        "XcodeProject" {
            title = "Xcode プロジェクト"
            summary = "app/ios 以下。Gradle の管理外で、検査もしない"
            example("iosAppApp.swift", "SwiftUI のエントリポイント")
            layout {
                "app/ios" { ignore() }
            }
        }
    }
}
