package com.example.groups

import com.example.roles.architectureDefinition
import com.example.roles.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the test code, including the architecture definition itself.
 *
 * The definition lives in `:architecture-test`, a module that belongs to no layer of the
 * application. It is still code someone has to maintain, so it gets a role of its own rather
 * than hiding inside `Test`: `ArchitectureDefinition` describes the shape, `Test` asserts
 * behaviour.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "テスト"
    summary = "振る舞いを確かめるテストと、この定義そのもの"

    description = """
        アプリのレイヤーではなく、プロジェクトを支えているコードを集めた場所です。
        テストコードと、アーキテクチャ定義そのものが入ります。

        2つを分けているのは対象が違うからです。テストは振る舞いを確かめ、アーキテクチャ定義は
        形を記述します。定義を `Test` の中に隠すと、`:architecture-test` というモジュールが
        なぜあるのかがドキュメントから消えてしまいます。

        ここに置いてはいけないのは、アプリの本体コードです。`:architecture-test` はアプリの
        どのレイヤーにも属さないモジュールで、main ソースセットを持ちません。
    """.trimIndent()

    test()
    architectureDefinition()
}
