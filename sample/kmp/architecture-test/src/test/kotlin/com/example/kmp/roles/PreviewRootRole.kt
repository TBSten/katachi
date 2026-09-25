package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The `preview` package of `:ui`.
 *
 * `PreviewRoot` is the only thing in it, and every `@Preview` in the sample goes through it
 * instead of writing `AppTheme { }` itself. Kept apart from [preview], whose name it is a
 * prefix of, because the two are different things: one is the wrapper, the other is what the
 * wrapper is used by.
 */
fun DeclarationContainerScope.previewRoot() = "PreviewRoot" {
    title = "プレビューの土台"
    summary = ":ui モジュールの preview package。@Preview の中身を AppTheme と Surface で包む"
    description = """
        プレビューを包む wrapper だけの package です。`PreviewRoot` は中身を `AppTheme` と
        `Surface` で包みます。`AppTheme` が本番と同じ配色を与え、`Surface` がその背景色を
        後ろに塗ります。これが無いと、すべてのプレビューが同じ2行を書き写すことになり、
        テーマに引数が増えた日に一斉に食い違います。

        名前が似ている ui/Preview とは別の役割です。こちらは包む側、あちらは包まれる側で、
        ここに `@Preview` を1つも書きません。逆に、プレビュー以外から `PreviewRoot` を
        呼ぶこともありません。

        `commonTest` ではなく `commonMain` に置いてあるのは、使う側の `@Preview` が
        feature モジュールの `commonMain` にいるからです。テスト source set は他モジュールから
        参照できないので、そこに置くと届きません。

        この役割はワイルドカードを使わずファイル名を書ききっている数少ない宣言なので、
        `PreviewRoot.kt` が消えたり改名されたりすると `[MissingFile]` で報告されます。
    """.trimIndent()
    example("PreviewRoot", "すべての @Preview が使う wrapper")
    // One of the file names in this sample written without a wildcard, so it is also
    // one of the declarations that is reported as `[MissingFile]` when it disappears.
    layout {
        ":ui".module {
            "commonMain".sourceSet / kotlin / modulePackage / "preview" / "PreviewRoot".ktFile()
        }
    }
}
