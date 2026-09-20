package com.example.kmp.app

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.architecture

/**
 * The architecture of this sample, described with katachi.
 *
 * Step 1 only declares groups and roles; every `layout { }` is still empty. The paths each
 * role may occupy arrive in step 2, which is also when the definition starts to be checked
 * against the file system.
 *
 * Kept in a top level `val`: building it reads nothing and runs no check, so the same value
 * can be shared by every test and, later, by documentation generation.
 */
val projectArchitecture: Architecture = architecture {
    uiRoles()
    dataRoles()
    testingRoles()
    appRoles()
    buildRoles()
}

/** Everything a screen is made of, plus the shared UI modules it draws from. */
private fun ArchitectureScope.uiRoles() {
    "ui".group {
        title = "UI"

        "Screen" {
            title = "画面"
            summary = "1つの画面。ViewModel の状態を受け取り、Component を組み合わせて描く"
            example("HomeScreen", "ホーム画面")
            example("SettingsScreen", "設定画面")
            layout { }
        }
        "ViewModel" {
            title = "ViewModel"
            summary = "画面の状態を持ち、Repository から取得した値を UiState に変換する"
            example("HomeViewModel", "ホーム画面の状態")
            layout { }
        }
        "Route" {
            title = "ルート"
            summary = "画面を navigation の Destination に結びつける"
            example("HomeRoute", "ホーム画面の遷移先")
            layout { }
        }
        "Component" {
            title = "共通コンポーネント"
            summary = "複数の画面から使われる UI 部品"
            example("PrimaryButton", "主要な操作のボタン")
            layout { }
        }
        "Theme" {
            title = "テーマ"
            summary = "色・余白などのデザイントークン"
            example("AppTheme", "アプリ全体のトークン")
            layout { }
        }
        "UiCore" {
            title = "UI 基盤"
            summary = "画面に依存しない UI の土台。UiState など"
            example("UiState", "画面の状態を表す型")
            layout { }
        }
        "Navigation" {
            title = "ナビゲーション"
            summary = "遷移先の定義と画面の繋ぎ込み"
            example("Destination", "遷移先の一覧")
            layout { }
        }
    }
}

/** The data layer, including the parts that differ per platform. */
private fun ArchitectureScope.dataRoles() {
    "data".group {
        title = "データ"

        "Repository" {
            title = "リポジトリ"
            summary = "データの取得口。インターフェースと実装の2つの置き方を持つ"
            example("UserRepository", "ユーザーを取得するインターフェース")
            example("UserRepositoryImpl", "UserRepository の実装")
            layout { }
        }
        // The role that only exists because this is a KMP project: `commonMain` declares
        // `expect`, and `androidMain` / `iosMain` supply the `actual`.
        "PlatformImplementation" {
            title = "プラットフォーム実装"
            summary = "commonMain の expect 宣言に対する、androidMain / iosMain の actual 実装"
            example("PlatformInfo.android.kt", "Android 向けの actual")
            example("PlatformInfo.ios.kt", "iOS 向けの actual")
            layout { }
        }
    }
}

/** Test doubles, and the test code itself. */
private fun ArchitectureScope.testingRoles() {
    "testing".group {
        title = "テスト支援"

        "Fake" {
            title = "フェイク"
            summary = ":testing の commonMain に置く偽の実装。他モジュールのテストから使う"
            example("FakeUserRepository", "UserRepository の偽実装")
            layout { }
        }
        // Android puts its tests in `src/test`; a KMP module puts them in `commonTest`.
        // Both shapes are one role here.
        "Test" {
            title = "テストコード"
            summary = "各モジュールのテスト。KMP モジュールは commonTest、純 Android モジュールは src/test"
            example("ProjectArchitectureSpec", "この定義そのものを検証するテスト")
            layout { }
        }
    }
}

/** The two applications: the Android one Gradle builds, and the iOS one Xcode builds. */
private fun ArchitectureScope.appRoles() {
    "app".group {
        title = "アプリ"

        "Entrypoint" {
            title = "エントリポイント"
            summary = "Android アプリの起動点"
            example("MainActivity", "起動時に表示される Activity")
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

/**
 * Build scripts and VCS files. Not documented: they are part of the repository's shape but
 * not part of the architecture a reader of the docs needs.
 *
 * `documented = false` is written on the group and on each role, because katachi keeps the
 * declared value as written and does not inherit it from the parent.
 */
private fun ArchitectureScope.buildRoles() {
    "build".group(documented = false) {
        title = "ビルド"

        "GradleModule" {
            title = "モジュールのビルドスクリプト"
            summary = "各モジュールの build.gradle.kts"
            documented = false
            example("data/build.gradle.kts", ":data のビルドスクリプト")
            layout { }
        }
        "GradleRoot" {
            title = "ルートのビルドファイル"
            summary = "settings.gradle.kts、ルートの build.gradle.kts、gradle.properties、wrapper"
            documented = false
            example("settings.gradle.kts", "モジュール構成と catalog の宣言")
            layout { }
        }
        "Git" {
            title = "Git の設定"
            summary = ".gitignore など"
            documented = false
            example(".gitignore", "生成物を Git の管理から外す")
            layout { }
        }
    }
}
