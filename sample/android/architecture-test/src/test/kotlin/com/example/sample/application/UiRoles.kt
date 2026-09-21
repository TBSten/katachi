package com.example.sample.application

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * Roles of the shared UI: what `:ui` and `:navigation` hold.
 *
 * The per-feature roles (`Screen` / `ViewModel` / `Route`) live in [featureRoles]
 * instead. See the note there for why the two are separate groups.
 *
 * `:ui` is one module split into packages, which is the shape `modulePackage` exists for:
 * `component` / `theme` / `core` / `preview` are named as what they are — one more level
 * below the module's own package — and `mainSourceSet / kotlin / modulePackage` says where
 * that package starts without this file ever repeating `com/example/sample`.
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
            layout {
                ":ui".module {
                    mainSourceSet / kotlin / modulePackage / "component" / "*".ktFile()
                }
            }
        }

        "Theme" {
            title = "テーマ"
            summary = ":ui モジュールの theme package に置く、色・タイポグラフィ・形"
            example("AppTheme", "アプリのテーマ")
            layout {
                ":ui".module {
                    // Named exactly, not `*.kt`: there is one theme, and a second file
                    // turning up here should be a violation rather than a silent second
                    // theme.
                    mainSourceSet / kotlin / modulePackage / "theme" / "AppTheme".ktFile()
                }
            }
        }

        "UiCore" {
            title = "UI 基盤"
            summary = ":ui モジュールの core package に置く、UI 層の土台になる型"
            example("UiState", "画面状態を表す sealed interface")
            layout {
                ":ui".module {
                    mainSourceSet / kotlin / modulePackage / "core" / "*".ktFile()
                }
            }
        }

        "Preview" {
            title = "プレビュー"
            summary = "@Preview を付けた private @Composable。対象の Composable と同じファイルに置き、" +
                "中身は PreviewRoot で包む"
            example("AppButtonFilledPreview", "AppButton のプレビュー")
            example("HomeScreenContentPreview", "HomeScreen のプレビュー")
            // Deliberately empty. In this sample a preview is a function inside the file of
            // the Composable it previews, so it owns no path of its own: claiming one here
            // would duplicate what `Component` and `Screen` already allow.
            // TODO(step 4): state what this role really means with `konsist { }` — a
            //  `@Preview` function is private, is a @Composable, and wraps its body in
            //  `PreviewRoot`. Until then the role carries documentation only.
            layout { }
        }

        "PreviewRoot" {
            title = "プレビューの土台"
            summary = ":ui モジュールの preview package に置く、すべての @Preview が中身を包む土台。" +
                "テーマと背景を 1 箇所で決め、darkTheme を受け取って明暗を出し分ける"
            example("PreviewRoot", "プレビュー共通の土台")
            layout {
                ":ui".module {
                    // Required, so deleting the file fails the check with `[MissingFile]`
                    // rather than leaving every `@Preview` without a base.
                    mainSourceSet / kotlin / modulePackage / "preview" / "PreviewRoot".ktFile()
                }
            }
        }

        "Navigation" {
            title = "画面遷移"
            summary = ":navigation に置く、画面間の移動"
            example("AppNavigator", "画面遷移の窓口")
            layout {
                ":navigation".module {
                    mainSourceSet / kotlin / modulePackage / "*".ktFile()
                }
            }
        }
    }
}
