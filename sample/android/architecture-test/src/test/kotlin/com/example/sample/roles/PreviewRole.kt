package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of a `@Preview` function, which owns no file of its own. */
fun DeclarationContainerScope.preview() = "Preview" {
    title = "プレビュー"
    summary = "@Preview を付けた private @Composable。対象の Composable と同じファイルに置き、" +
        "中身は PreviewRoot で包む"
    description = """
        Android Studio のプレビューに出すためだけの `private` な `@Composable`。
        対象の Composable と**同じファイル**の末尾に置く。`:ui` の共通コンポーネントにも
        `:feature:*` の Screen にもある。この役割だけ `layout { }` が空なのはそのためで、
        自分のファイルを1つも持たない。置き場所は共通コンポーネント役割と Screen 役割が
        すでに許しており、ここで重ねて書くと同じ場所を2回宣言することになる。

        中身は必ず `:ui` の `preview` package にある `PreviewRoot { }` で包む。
        各プレビューが `AppTheme { }` を直接書くと、背景の有無やテーマの渡し方が
        プレビューごとにずれていく（プレビューの土台役割を参照）。

        渡すのは状態だけ。`HomeScreenContentPreview` は `UiState.Content(...)` を、
        `HomeScreenLoadingPreview` は `UiState.Loading` を渡して、同じ画面の別の状態を並べる。
        `viewModel()` を取る方のオーバーロードはプレビューしない。明暗を並べたいときは
        `PreviewRoot(darkTheme = true)` を使う（`AppButtonFilledDarkPreview`）。

        この約束はいまのところ検査していない。「private であること」「`@Composable` であること」
        「`PreviewRoot` で包むこと」はどれもファイルの置き場所では表せないので、
        `konsist { }` が入るまでは文章だけの役割になっている。
    """.trimIndent()
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
