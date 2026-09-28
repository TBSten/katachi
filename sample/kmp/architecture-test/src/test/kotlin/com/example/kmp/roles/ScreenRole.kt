package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The role of one screen's UI: the `@Composable` its feature module is named after.
 *
 * `wildcard("feature")` is what `:feature:*` matched — `home` for `:feature:home` — so the file
 * required of that module is `HomeScreen.kt` and nothing else: a `ProfileScreen.kt` under
 * `:feature:home` is an `[UnexpectedFile]`, and it is `[MissingFile]` that reports a feature
 * whose screen was renamed. Written as a plain `*Screen.kt` glob the check would have accepted
 * both.
 */
fun DeclarationContainerScope.screen() = "Screen" {
    title = "画面"
    summary = "1つの画面の @Composable。ViewModel の StateFlow を購読し、Component を組み合わせて描く"
    description = """
        1つの画面まるごとの見た目です。`:feature:<name>` の `commonMain` に `<Name>Screen.kt` を
        1ファイルだけ置きます。ファイル名はモジュール名から決まるので、`:feature:home` に
        `ProfileScreen.kt` を足すことはできません。画面を増やすときはモジュールを増やします。

        1ファイルの中は2段構えにしています。`HomeScreen` が ViewModel を受け取って
        `collectAsState()` で購読し、`internal fun HomeContent` が `UiState` を引数で受け取って描く。
        ViewModel を作らないと描けないものと、状態を渡せば描けるものを分けておくと、
        プレビュー（ui/Preview）もテストも後者だけを相手にできます。

        `commonMain` だけなのは、Compose Multiplatform で Android と iOS が同じ画面コードを
        共有するためです。プラットフォームで挙動が変わる処理が要るなら、画面ではなく `:data` の
        PlatformImplementation（expect/actual）に降ろしてください。
    """.trimIndent()
    allowedContents = """
        - その画面の `@Composable` と、状態を引数で受け取る stateless な `<Name>Content`
        - `:ui` の Component / Theme / UiCore を組み合わせる配置の記述
    """.trimIndent()
    forbiddenContents = """
        - Repository の直接呼び出し。データは ViewModel が `UiState` にしてから渡します
        - 他の feature モジュールの画面や、その内部の型
        - `androidMain` / `iosMain` 版の画面。この役割は `commonMain` しか宣言していません
    """.trimIndent()
    example("HomeScreen", "ホーム画面")
    example("SettingsScreen", "設定画面")
    layout {
        ":feature:*".module(capture = "feature") {
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcard("feature").pascalCase}Screen".ktFile()
        }
    }
}
