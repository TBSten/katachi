package com.example.kmp.application

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * The shared UI modules a screen draws from: `:ui` and `:navigation`.
 *
 * The per-feature roles (`Screen` / `ViewModel` / `Route`) live in [featureRoles]
 * instead. See the note there for why the two are separate groups.
 *
 * Not `inline`: an inlined call reports the caller's file with a line number remapped past
 * the end of that file, so every declaration below would record a position that does not
 * exist.
 */
fun ArchitectureScope.uiRoles() {
    "ui".group {
        title = "UI"

        // Component / Theme / UiCore all live in the one `:ui` module. They used to be
        // `:ui:component` / `:ui:theme` / `:ui:core`; now they are packages of `:ui`, which
        // is the shape katachi has to be able to describe.
        "Component" {
            title = "共通コンポーネント"
            summary = ":ui モジュールの component package。複数の画面から使われる @Composable 部品"
            example("PrimaryButton", "主要な操作のボタン")
            layout {
                "ui/src/commonMain/kotlin" {
                    "com/example/kmp/ui/component" / "*".ktFile()
                }
            }
        }
        "Theme" {
            title = "テーマ"
            summary = ":ui モジュールの theme package。MaterialTheme の設定と、色・余白のデザイントークン"
            example("AppTheme", "アプリ全体のテーマ")
            example("AppSpacing", "余白のトークン")
            layout {
                "ui/src/commonMain/kotlin" {
                    "com/example/kmp/ui/theme" / "*".ktFile()
                }
            }
        }
        "UiCore" {
            title = "UI 基盤"
            summary = ":ui モジュールの core package。画面に依存しない UI の土台。UiState など"
            example("UiState", "画面の状態を表す型")
            layout {
                "ui/src/commonMain/kotlin" {
                    "com/example/kmp/ui/core" / "*".ktFile()
                }
            }
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
            // A preview lives beside what it renders, so this role claims a file name in two
            // different modules instead of a directory of its own. `component/*.kt` of
            // `Component` covers the same file as well: two roles may claim one path, and
            // from v0.3 the generated documentation lists both.
            layout {
                "feature" / "*" / "src/commonMain/kotlin" {
                    "com/example/kmp/feature" / "*" / "*Preview".ktFile()
                }
                "ui/src/commonMain/kotlin" {
                    "com/example/kmp/ui/component" / "*Preview".ktFile()
                }
            }
        }
        // The `preview` package of `:ui`. `PreviewRoot` is the only thing in it, and every
        // `@Preview` in the sample goes through it instead of writing `AppTheme { }` itself.
        "PreviewRoot" {
            title = "プレビューの土台"
            summary = ":ui モジュールの preview package。@Preview の中身を AppTheme と Surface で包む"
            example("PreviewRoot", "すべての @Preview が使う wrapper")
            // The one file name in this sample written without a wildcard, so it is also the
            // one declaration that is reported as `[MissingFile]` when it disappears.
            layout {
                "ui/src/commonMain/kotlin" {
                    "com/example/kmp/ui/preview" / "PreviewRoot".ktFile()
                }
            }
        }
        "Navigation" {
            title = "ナビゲーション"
            summary = "遷移先の定義と、現在地を持つ Navigator"
            example("Destination", "遷移先の一覧")
            example("Navigator", "現在の遷移先を StateFlow で持つ")
            layout {
                "navigation/src/commonMain/kotlin" {
                    "com/example/kmp/navigation" / "*".ktFile()
                }
            }
        }
    }
}
