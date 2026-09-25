package com.example.groups

import com.example.roles.architectureDefinition
import com.example.roles.generatedDocumentation
import com.example.roles.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the test code, the architecture definition itself, and what that definition
 * generates.
 *
 * The definition lives in `:architecture-test`, a module that belongs to no layer of the
 * application. It is still code someone has to maintain, so it gets a role of its own rather
 * than hiding inside `Test`: `ArchitectureDefinition` describes the shape, `Test` asserts
 * behaviour, and `GeneratedDocumentation` is what the shape is written out as.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "テスト"
    summary = "振る舞いを確かめるテストと、この定義そのもの、そこから生成されるドキュメント"

    description = """
        アプリのレイヤーではなく、プロジェクトを支えているコードを集めた場所です。
        テストコード、アーキテクチャ定義そのもの、そしてその定義から生成される `docs/` が
        入ります。

        テストと定義を分けているのは対象が違うからです。テストは振る舞いを確かめ、
        アーキテクチャ定義は形を記述します。定義を `Test` の中に隠すと、`:architecture-test`
        というモジュールがなぜあるのかがドキュメントから消えてしまいます。

        生成ドキュメントも定義と分けてあります。定義は人が書くもの、`docs/` は
        `--processor=docs` が書くもので、手を入れてよい場所がまるで違うからです。
        `build/` の外に出した以上、生成物にも役割が要る、という例にもなっています。

        ここに置いてはいけないのは、アプリの本体コードです。`:architecture-test` はアプリの
        どのレイヤーにも属さないモジュールで、main ソースセットを持ちません。
    """.trimIndent()

    test()
    architectureDefinition()
    generatedDocumentation()
}
