package com.example.sample

import me.tbsten.katachi.dsl.architecture

/**
 * The architecture of this sample, written the way a user of katachi would write it.
 *
 * Step 1 only declares groups and roles: every `layout { }` is still empty, and filling
 * them in is what step 2 is for. Building this value reads nothing from disk, so it is
 * safe to hold in a top level `val`.
 *
 * Keep the declarations in this file. [ProjectArchitectureSpec] asserts that a role's
 * `declaredAt.fileName` is `ProjectArchitecture.kt`, which is how the sample notices if
 * capturing the declaration site ever breaks in a real Android unit test run.
 */
val projectArchitecture = architecture {
    "ui".group {
        title = "UI"

        "Screen" {
            title = "画面"
            summary = "1つの画面の見た目を描く @Composable。:feature:<name> ごとに <Name>Screen.kt を置く"
            example("HomeScreen", "ホーム画面")
            example("SettingsScreen", "設定画面")
            layout { }
        }

        "ViewModel" {
            title = "ビューモデル"
            summary = "画面の状態を StateFlow で公開し、イベントを受け取る androidx.lifecycle.ViewModel"
            example("HomeViewModel", "ホーム画面の状態")
            layout { }
        }

        "Route" {
            title = "ルート"
            summary = "画面への遷移先。feature の外に公開する唯一の入口"
            example("HomeRoute", "ホーム画面への遷移先")
            layout { }
        }

        "Component" {
            title = "共通コンポーネント"
            summary = ":ui モジュールの component package に置く、feature をまたいで使う部品"
            example("AppButton", "アプリ共通のボタン")
            layout { }
        }

        "Theme" {
            title = "テーマ"
            summary = ":ui モジュールの theme package に置く、色・タイポグラフィ・形"
            example("AppTheme", "アプリのテーマ")
            layout { }
        }

        "UiCore" {
            title = "UI 基盤"
            summary = ":ui モジュールの core package に置く、UI 層の土台になる型"
            example("UiState", "画面状態を表す sealed interface")
            layout { }
        }

        "Preview" {
            title = "プレビュー"
            summary = "@Preview を付けた private @Composable。対象の Composable と同じファイルに置き、" +
                "中身は PreviewRoot で包む"
            example("AppButtonFilledPreview", "AppButton のプレビュー")
            example("HomeScreenContentPreview", "HomeScreen のプレビュー")
            layout { }
        }

        "PreviewRoot" {
            title = "プレビューの土台"
            summary = ":ui モジュールの preview package に置く、すべての @Preview が中身を包む土台。" +
                "テーマと背景を 1 箇所で決め、darkTheme を受け取って明暗を出し分ける"
            example("PreviewRoot", "プレビュー共通の土台")
            layout { }
        }

        "Navigation" {
            title = "画面遷移"
            summary = ":navigation に置く、画面間の移動"
            example("AppNavigator", "画面遷移の窓口")
            layout { }
        }
    }

    "data".group {
        title = "データ"

        "Repository" {
            title = "リポジトリ"
            summary = "データの取得と保存。インターフェースと実装を :data の、扱う対象ごとの package " +
                "（user / settings）に並べて置く"
            example("UserRepository", "ユーザーの取得と保存のインターフェース")
            example("UserRepositoryImpl", "UserRepository の実装")
            // Two layouts: a role may live in more than one place. Here the interface
            // (`*Repository.kt`) and the implementation (`*RepositoryImpl.kt`).
            layout { }
            layout { }
        }
    }

    "testing".group {
        title = "テスト"

        "Fake" {
            title = "フェイク"
            summary = ":testing に置く、他モジュールのテストから使う偽の実装"
            example("FakeUserRepository", "UserRepository のメモリ実装")
            layout { }
        }

        "Test" {
            title = "テストコード"
            summary = "各モジュールの src/test/kotlin に置くテストそのもの"
            example("ProjectArchitectureSpec", "この定義そのものを検証するテスト")
            layout { }
        }
    }

    "app".group {
        title = "アプリ"

        "Entrypoint" {
            title = "エントリポイント"
            summary = ":app に置く、Android がアプリを起動するときに触る型"
            example("MainActivity", "起動時に表示される Activity")
            example("MainApplication", "Application の実装")
            layout { }
        }

        "AndroidResource" {
            title = "Android リソース"
            summary = "AndroidManifest.xml・res/・proguard-rules.pro"
            example("AndroidManifest.xml", "アプリの構成")
            example("res/values/strings.xml", "文字列リソース")
            layout { }
        }
    }

    // Build files are checked but not documented: they are the same in every project and
    // say nothing about this app.
    //
    // `documented` is not inherited (a declared value is kept as written), so each role
    // below has to say `documented = false` for itself.
    "build".group(documented = false) {
        title = "ビルド"

        "GradleModule" {
            title = "モジュールのビルドスクリプト"
            summary = "各モジュールの build.gradle.kts"
            documented = false
            layout { }
        }

        "GradleRoot" {
            title = "ルートのビルドスクリプト"
            summary = "settings.gradle.kts・ルートの build.gradle.kts・gradle.properties・wrapper"
            documented = false
            layout { }
        }

        // No `title` here on purpose: an undocumented role has no display name to show,
        // and the sample asserts that the role name is then used as-is.
        "Git" {
            summary = ".gitignore など"
            documented = false
            layout { }
        }
    }
}
