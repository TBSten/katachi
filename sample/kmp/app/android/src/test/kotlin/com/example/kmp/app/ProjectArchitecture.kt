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
            summary = "1つの画面の @Composable。ViewModel の StateFlow を購読し、Component を組み合わせて描く"
            example("HomeScreen", "ホーム画面")
            example("SettingsScreen", "設定画面")
            layout { }
        }
        "ViewModel" {
            title = "ViewModel"
            summary = "画面の状態を持つ androidx.lifecycle.ViewModel。" +
                "Repository から取得した値を UiState に変換し、StateFlow で公開する"
            example("HomeViewModel", "ホーム画面の状態")
            layout { }
        }
        "Route" {
            title = "ルート"
            summary = "画面を navigation の Destination に結びつけ、ViewModel の生成も引き受ける"
            example("HomeRoute", "ホーム画面の遷移先")
            layout { }
        }
        // Component / Theme / UiCore all live in the one `:ui` module. They used to be
        // `:ui:component` / `:ui:theme` / `:ui:core`; now they are packages of `:ui`, which
        // is the shape katachi has to be able to describe.
        "Component" {
            title = "共通コンポーネント"
            summary = ":ui モジュールの component package。複数の画面から使われる @Composable 部品"
            example("PrimaryButton", "主要な操作のボタン")
            layout { }
        }
        "Theme" {
            title = "テーマ"
            summary = ":ui モジュールの theme package。MaterialTheme の設定と、色・余白のデザイントークン"
            example("AppTheme", "アプリ全体のテーマ")
            example("AppSpacing", "余白のトークン")
            layout { }
        }
        "UiCore" {
            title = "UI 基盤"
            summary = ":ui モジュールの core package。画面に依存しない UI の土台。UiState など"
            example("UiState", "画面の状態を表す型")
            layout { }
        }
        // Unlike sample/android, which keeps its `@Preview` functions in the same file as the
        // composable they render, this sample puts them in a `<Target>Preview.kt` file beside
        // it. Both shapes are common; having one sample of each is the point.
        "Preview" {
            title = "プレビュー"
            summary = "@Preview を付けた private @Composable。対象の Composable と同じ package の " +
                "<対象>Preview.kt に置き、中身は PreviewRoot で包む"
            example("PrimaryButtonPreview", "PrimaryButton のプレビュー")
            example("HomeLoadedPreview", "読み込み済みの HomeContent のプレビュー")
            layout { }
        }
        // The `preview` package of `:ui`. `PreviewRoot` is the only thing in it, and every
        // `@Preview` in the sample goes through it instead of writing `AppTheme { }` itself.
        "PreviewRoot" {
            title = "プレビューの土台"
            summary = ":ui モジュールの preview package。@Preview の中身を AppTheme と Surface で包む"
            example("PreviewRoot", "すべての @Preview が使う wrapper")
            layout { }
        }
        "Navigation" {
            title = "ナビゲーション"
            summary = "遷移先の定義と、現在地を持つ Navigator"
            example("Destination", "遷移先の一覧")
            example("Navigator", "現在の遷移先を StateFlow で持つ")
            layout { }
        }
    }
}

/** The data layer, including the parts that differ per platform. */
private fun ArchitectureScope.dataRoles() {
    "data".group {
        title = "データ"

        // `:data` is one module split into packages, the same way `:ui` is.
        // `user` holds the repositories, `platform` the expect/actual pair. There is no
        // `settings` package here: unlike sample/android this sample has no
        // SettingsRepository, and the settings screen reads `:data` through UserRepository
        // and `platformName()`.
        "Repository" {
            title = "リポジトリ"
            summary = ":data モジュールの user package。データの取得口で、" +
                "インターフェースと実装の2つの置き方を持つ"
            example("UserRepository", "ユーザーを取得するインターフェース")
            example("UserRepositoryImpl", "UserRepository の実装")
            layout { }
        }
        // The role that only exists because this is a KMP project: `commonMain` declares
        // `expect`, and `androidMain` / `iosMain` supply the `actual`. The three files have
        // to sit in the same package, so the package is part of what this role describes.
        "PlatformImplementation" {
            title = "プラットフォーム実装"
            summary = ":data モジュールの platform package。commonMain の expect 宣言と、" +
                "androidMain / iosMain の actual 実装が同じ package に揃う"
            example("PlatformInfo.kt", "commonMain の expect 宣言")
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
        // No `title` here on purpose: an undocumented role has no display name to show, so
        // this is the one place in this sample that exercises the default — the role name
        // itself. `ProjectArchitectureSpec` asserts it.
        "Git" {
            summary = ".gitignore など"
            documented = false
            example(".gitignore", "生成物を Git の管理から外す")
            layout { }
        }
    }
}
