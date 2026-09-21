package com.example.sample.application

import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutScope

/**
 * Roles of the shared UI: what `:ui` and `:navigation` hold.
 *
 * The per-feature roles (`Screen` / `ViewModel` / `Route`) live in [featureRoles]
 * instead. See the note there for why the two are separate groups.
 *
 * `:ui` is one module split into packages, which is what makes it the most verbose layout
 * of the sample in step 2: `component` / `theme` / `core` / `preview` are reached by writing
 * out `src/main/kotlin` and the whole base package by hand ([uiSources]). Step 3 replaces
 * all of that with `mainSourceSet / kotlin / modulePackage`, and the contrast is the point.
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
                uiSources("component") / "*".ktFile()
            }
        }

        "Theme" {
            title = "テーマ"
            summary = ":ui モジュールの theme package に置く、色・タイポグラフィ・形"
            example("AppTheme", "アプリのテーマ")
            layout {
                // Named exactly, not `*.kt`: there is one theme, and a second file turning
                // up here should be a violation rather than a silent second theme.
                uiSources("theme") / "AppTheme".ktFile()
            }
        }

        "UiCore" {
            title = "UI 基盤"
            summary = ":ui モジュールの core package に置く、UI 層の土台になる型"
            example("UiState", "画面状態を表す sealed interface")
            layout {
                uiSources("core") / "*".ktFile()
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
                // Required, so deleting the file fails the check with `[MissingFile]` rather
                // than leaving every `@Preview` without a base.
                uiSources("preview") / "PreviewRoot".ktFile()
            }
        }

        "Navigation" {
            title = "画面遷移"
            summary = ":navigation に置く、画面間の移動"
            example("AppNavigator", "画面遷移の窓口")
            layout {
                "navigation" / "src" / "main" / "kotlin" / "com" / "example" / "sample" /
                    "navigation" / "*".ktFile()
            }
        }
    }
}

/**
 * One package of `:ui`: `ui/src/main/kotlin/com/example/sample/ui/<packageName>`.
 *
 * Every level is a plain directory, because step 2 has no notion of a Gradle module, a
 * source set or a base package. Step 3 writes the same thing as
 * `mainSourceSet / kotlin / modulePackage / packageName`.
 */
private fun LayoutScope.uiSources(packageName: String): LayoutDirectory =
    "ui" / "src" / "main" / "kotlin" / "com" / "example" / "sample" / "ui" / packageName
