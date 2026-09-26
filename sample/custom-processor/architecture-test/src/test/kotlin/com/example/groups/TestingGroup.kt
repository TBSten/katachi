package com.example.groups

import com.example.roles.architectureDefinition
import com.example.roles.generatedDocumentation
import com.example.roles.layoutSnapshot
import com.example.roles.processor
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The architecture definition, the processors this project writes, and what the two produce.
 *
 * The code all lives in `:architecture-test`, a module that belongs to no layer of the
 * application, and it is split into roles rather than kept as one because the direction of
 * reading differs: the definition describes the shape, a processor reads that shape, and `docs/`
 * and `snapshots/` are what was written out of it.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "定義とプロセッサ"
    summary = "katachi の定義、それを読む自作プロセッサ3本、そして生成されたドキュメント"

    description = """
        このサンプルの本題が置かれている場所です。アプリのレイヤーではなく、プロジェクトを
        支えているコードを集めてあります。

        役割を分けているのは、読む向きが違うからです。アーキテクチャ定義は形を記述し、
        プロセッサはその形を読んで何かを作り、生成ドキュメントとレイアウトスナップショットは
        書き出された結果です。`processors/` を定義の役割に含めてしまうと、このサンプルが
        何を見せたいのかが `docs/` からも `RoleFileCount` の出力からも消えてしまいます。

        プロセッサの3本は、引数なし・型付き引数・検査（`Result.failure` で落とす）という
        3つの形を1本ずつ受け持ちます。どれも `object` で、katachi 側に継承すべき基底クラスは
        ありません。

        生成物の2つも役割が別です。`docs/` は `katachiDocs` が読む人のために書くもの、
        `snapshots/` は `LayoutSnapshotSpec` が katachi 自身のために書くもので、
        更新の仕方も消したときに困る相手も違います。

        ここに置いてはいけないのは、アプリの本体コードです。`:architecture-test` は
        main ソースセットを持ちません。
    """.trimIndent()

    architectureDefinition()
    processor()
    generatedDocumentation()
    layoutSnapshot()
}
