package com.example.sample.roles

import com.example.sample.groups.featureSources
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The role of one screen's UI: the `@Composable` its feature module is named after.
 *
 * `wildcards[0]` is the module's own name, so a file is not merely allowed to be *a* screen
 * but has to be **that module's** screen: `:feature:home` may hold `HomeScreen.kt` and nothing
 * else called `*Screen.kt`.
 */
fun DeclarationContainerScope.screen() = "Screen" {
    title = "Screen"
    summary = "1つの画面の UI 実装となる @Composable。:feature:<name> ごとに <Name>Screen.kt を置く"
    description = """
        画面そのものを描く `@Composable`。`:feature:home` なら `HomeScreen.kt` が1つ、という
        対応が固定で、ファイル名はモジュール名から決まる。`:feature:home` に `ProfileScreen.kt` を
        置くことはできず、`HomeScreen.kt` を消すこともできない。画面が2つになったら
        feature モジュールごと分ける。

        1つのファイルに `HomeScreen` を2つ重ねて置く。ナビゲーションから呼ばれる public な方は
        `viewModel()` を既定引数で受け取り、`collectAsStateWithLifecycle()` で状態を集めて
        もう一方へ渡すだけ。`internal` な方は `UiState<HomeContent>` とコールバックだけを
        受け取る状態の関数で、`@Preview` が触るのはこちら。

        ここに置いてよいもの:

        - 画面のレイアウトと、`UiState` の `Loading` / `Content` / `Error` の出し分け
        - `:ui` の共通コンポーネント（`AppButton` など）と Material3 の呼び出し
        - この画面のための `@Preview`（プレビュー役割を参照）

        置いてはいけないもの:

        - 状態の組み立てと保持。ViewModel の仕事で、`HomeContent` のような状態の型も
          ViewModel と同じファイルに置く
        - `NavHostController` への依存。画面から出ていく遷移は、引数で受け取った
          コールバック（`onNavigateToSettings` / `onNavigateUp`）を呼ぶだけにする
        - 他の feature の型。feature 同士は互いを参照せず、`:app` が Route 越しにつなぐ
    """.trimIndent()
    example("HomeScreen", "ホーム画面")
    example("SettingsScreen", "設定画面")
    layout {
        ":feature:*".module {
            featureSources() / "${wildcards[0].pascalCase}Screen".ktFile()
        }
    }
}
