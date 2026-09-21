package com.example.sample.application

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the shared UI: what `:ui` and `:navigation` hold.
 *
 * The per-feature roles (`Screen` / `ViewModel` / `Route`) live in [featureRoles]
 * instead. See the note there for why the two are separate groups.
 *
 * Deliberately not `inline`. katachi reads the declaration site off the stack trace, and an
 * inlined frame reports a line number remapped past the end of the caller's file, so every
 * role declared here would point at a line that does not exist.
 */
fun ArchitectureScope.uiRoles() {
    "ui".group {
        title = "UI (共通レイヤー)"

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
}
