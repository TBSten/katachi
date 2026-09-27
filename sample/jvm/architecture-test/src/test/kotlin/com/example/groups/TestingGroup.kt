package com.example.groups

import com.example.forbiddenContents
import com.example.roles.architectureDefinition
import com.example.roles.baseline
import com.example.roles.generatedDocumentation
import com.example.roles.layoutSnapshot
import com.example.roles.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the test code, the architecture definition itself, and the two things that
 * definition is written out as.
 *
 * The definition lives in `:architecture-test`, a module that belongs to no layer of the
 * application. It is still code someone has to maintain, so it gets a role of its own rather
 * than hiding inside `Test`: `ArchitectureDefinition` describes the shape and `Test` asserts
 * behaviour. `GeneratedDocumentation` and `LayoutSnapshot` are the shape written out — as
 * pages for a reader, and as one flattened line per entry for a reviewer's `git diff`.
 * `Baseline` is the ledger of the violations held back, which the test writes too.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "テスト"
    summary = "振る舞いを確かめるテストと、この定義そのもの、そこから書き出されるドキュメントとスナップショット"

    description = """
        アプリのレイヤーではなく、プロジェクトを支えているコードを集めた場所です。
        テストコード、アーキテクチャ定義そのもの、そしてその定義から書き出される `docs/` と
        `snapshots/` が入ります。

        テストと定義を分けているのは対象が違うからです。テストは振る舞いを確かめ、
        アーキテクチャ定義は形を記述します。定義を `Test` の中に隠すと、`:architecture-test`
        というモジュールがなぜあるのかがドキュメントから消えてしまいます。

        書き出されたものも定義と分けてあります。定義は人が書くもの、`docs/` と `snapshots/` は
        機械が書くもので、手を入れてよい場所がまるで違うからです。`build/` の外に出した以上、
        生成物にも役割が要る、という例にもなっています。2つを1つにまとめていないのは、
        読み手が違うからです。`docs/` は定義を読みに来た人が開くページ、`snapshots/` は
        定義を書き換えた差分をレビューする人が見るテキストです。

        `katachi-baseline.json` も、テストが書き出すファイルとしてここに入ります。katachi を
        入れた時点ですでにあった違反の台帳で、そこに記録した違反はテストを落としません。
    """.trimIndent()

    forbiddenContents = """
        ここに置いてはいけないのは、アプリの本体コードです。`:architecture-test` はアプリの
        どのレイヤーにも属さないモジュールで、main ソースセットを持ちません。
    """.trimIndent()

    test()
    architectureDefinition()
    generatedDocumentation()
    layoutSnapshot()
    baseline()
}
