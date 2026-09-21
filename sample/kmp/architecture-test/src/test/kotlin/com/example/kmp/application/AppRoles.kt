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
            layout { }
        }
        "AndroidResource" {
            title = "Android リソース"
            summary = "AndroidManifest.xml、res/、proguard-rules.pro"
            example("AndroidManifest.xml", "アプリとモジュールのマニフェスト")
            example("res/values/strings.xml", "文字列リソース")
            layout { }
        }
        // `app/ios` is not a Gradle module. From step 2 this role is declared as ignored,
        // because Xcode owns what is inside it.
        "XcodeProject" {
            title = "Xcode プロジェクト"
            summary = "app/ios 以下。Gradle の管理外で、検査もしない"
            example("iosAppApp.swift", "SwiftUI のエントリポイント")
            layout { }
        }
    }
}
