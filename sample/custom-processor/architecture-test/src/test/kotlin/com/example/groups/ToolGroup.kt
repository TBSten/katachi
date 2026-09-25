package com.example.groups

import com.example.roles.documentation
import com.example.roles.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of what sits around the project without being the build or the application.
 *
 * Like the build group, these are checked but kept out of the generated documentation.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
    documented = false
    title = "ツール設定"
    summary = "ビルドでもアプリでもない、リポジトリ周辺のもの"

    description = """
        リポジトリに置かれて開発を支える、ビルド以外のものです。いまは Git 設定（`.gitignore`）と
        手書きのドキュメント（`README.md`）の2つが入っています。

        ビルドと同じく `documented = false` で、チェックはするが生成ドキュメントには出しません。
        `README.md` を `docs/` に出さないのは、それが `docs/` の内容と重なるからです。読む人が
        最初に開くのはルートの `README.md` で、生成ページの索引はその中からリンクします。

        エディタや CI の設定ファイルが増えたときは `.gitignore` の役割に混ぜず、このグループに
        役割を1つ足すのが想定している育て方です。
    """.trimIndent()

    git()
    documentation()
}
