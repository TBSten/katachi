package com.example.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the version control configuration. */
fun DeclarationContainerScope.git() = "Git" {
    title = "Git 設定"
    summary = "バージョン管理の設定ファイル"
    description = """
        Git に何を渡さないかを書くファイルです。このプロジェクトでは `.gitignore` の1つだけが
        この役割に入ります。

        単なる作業上の都合ではなく、katachi の検査範囲そのものに効きます。既定の
        `files = gitTracked()` は git が追跡しているファイルだけを検査に渡すので、
        `.gitignore` に書いたもの（`build/`, `.gradle/`, `.kotlin/`, `local.properties` など）は
        どの役割にも属さないまま通り過ぎます。逆に、追跡されているのに役割が無いファイルは
        落ちます。

        置いてはいけないのは Git 以外のツールの設定です。増えたら `.gitignore` の役割に混ぜず、
        `tool` グループに役割を1つ足します。この役割が属する `tool` グループは
        `documented = false` なので、生成ドキュメントには出ません。
    """.trimIndent()
    example(".gitignore", "管理対象から外すファイルの一覧")
    layout {
        ".gitignore".file()
    }
}
