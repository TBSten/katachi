package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The `@Preview` functions themselves.
 *
 * Unlike sample/android, which keeps its `@Preview` functions in the same file as the
 * composable they render, this sample puts them in a `<Target>Preview.kt` file beside it.
 * Both shapes are common; having one sample of each is the point.
 *
 * It is also the one role of this sample that opens two places instead of one, which is why
 * both of them carry a `description`: a role spread over more than one place has to say what
 * tells the two apart, and katachi reports `[MissingDescription]` when it does not.
 */
fun DeclarationContainerScope.preview() = "Preview" {
    title = "プレビュー"
    summary = "@Preview を付けた private @Composable。対象の Composable と同じ package の " +
        "<対象>Preview.kt に置き、中身は PreviewRoot で包む"
    description = """
        `@Preview` を付けた `private @Composable` です。描く対象と同じ package の
        `<対象>Preview.kt` に分けて書きます。対象のファイルに同居させる書き方もありますが、
        このサンプルはファイルを分ける方を採っています（同居する形は sample/android にあります）。

        置き場所は2つあります。画面のプレビューはその画面を持つ feature モジュールに、
        部品のプレビューはどの画面にも属さないので `:ui` に置きます。どちらも「描く対象の隣」
        という同じ規則から出てくる2箇所です。

        この `@Preview` は Compose Multiplatform の
        `org.jetbrains.compose.ui:ui-tooling-preview` のものです。注釈の完全修飾名は
        Android 専用の `androidx.compose.ui:ui-tooling-preview` とまったく同じなので、
        IDE の補完で後者を足してしまうと iOS ターゲットが解決できなくなります。
        `commonMain` でプレビューが書けているのは前者を使っているからです。
    """.trimIndent()
    allowedContents = """
        - 状態を引数で渡せる stateless な Composable のプレビュー。`HomeScreen` ではなく
          `HomeContent` を呼ぶので、ViewModel を組み立てずに描けます
        - 1つの対象につき状態ごとに複数。読み込み中・読み込み済み・失敗を並べて見られます
    """.trimIndent()
    forbiddenContents = """
        - `public` なプレビュー。他から呼ぶものではないので `private` にします
        - プレビューの中で `AppTheme { }` を直接書くこと。包むのは `PreviewRoot` の仕事です
        - 本物の Repository やネットワークに触る処理。値はリテラルで書きます
    """.trimIndent()
    example("PrimaryButtonPreview", "PrimaryButton のプレビュー")
    example("HomeLoadedPreview", "読み込み済みのホーム画面")
    // A preview lives beside what it renders, so this role claims a file name in two
    // different modules instead of a directory of its own. `component/*.kt` of
    // `Component` covers the same file as well: two roles may claim one path, and the
    // generated documentation shows the pattern on each role's page.
    //
    // katachi reports that overlap as `[AmbiguousLayout]` on the file both patterns match,
    // which is a Warning and never fails `assert()`. It is kept rather than designed away:
    // a preview belongs beside the component it renders, and the report saying so out loud
    // is what this sample wants to show — see `OmittedRoleSelfCheckSpec`, which pins it.
    //
    // In a feature module the name is tied to the module the same way the screen it
    // renders is: `:feature:home` may hold `Home*Preview.kt` and nothing else.
    layout {
        ":feature:*".module(capture = "feature") {
            description = "画面のプレビュー。その画面を持つ feature モジュールに置く"
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcard("feature").pascalCase}*Preview".ktFile()
        }
        ":ui".module {
            description = "部品のプレビュー。どの画面にも属さないので :ui に置く"
            "commonMain".sourceSet / kotlin / modulePackage / "component" / "*Preview".ktFile()
        }
    }
}
