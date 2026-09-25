package com.example.groups

import com.example.roles.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the tools around the project that are not the build itself.
 *
 * Like the build group, these are checked but kept out of the generated documentation.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
    documented = false
    title = "ツール設定"
    summary = "ビルドそのものではない、プロジェクト周辺のツールの設定"

    description = """
        リポジトリに置かれて開発を支える、ビルド以外のツールの設定です。いまは Git 設定の役割
        （`.gitignore`）だけが入っています。

        ビルドと同じく `documented = false` で、チェックはするが生成ドキュメントには出しません。
        エディタや CI の設定ファイルが増えたときは `.gitignore` の役割に混ぜず、このグループに
        役割を1つ足すのが想定している育て方です。
    """.trimIndent()

    git()
}
