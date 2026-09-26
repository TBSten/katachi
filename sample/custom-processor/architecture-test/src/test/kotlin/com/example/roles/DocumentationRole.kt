package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the prose a human writes for whoever opens this sample. */
fun DeclarationContainerScope.documentation() = "Documentation" {
    title = "手書きのドキュメント"
    summary = "README.md など、このサンプルを開いた人に向けた説明"
    description = """
        人が書いてコミットする散文です。いまはルートの `README.md` 1つで、このサンプルが
        3本の processor で何を見せているのかを書いています。

        生成ドキュメントの役割とはちょうど裏表です。`docs/` は定義から書き出されるもので
        手を入れると消え、`README.md` は手で書くもので生成では触られません。

        `layout { }` は `README.md` という名前ちょうどを要求します。消したり名前を変えたりすれば
        違反になります。
    """.trimIndent()
    allowedContents = """
        置いてよいのは、定義からは出てこないことだけです。役割の説明は `description` に
        書けば `docs/` に出るので、README には書かない。写しを置けば必ず片方が古くなります。
    """.trimIndent()
    forbiddenContents = """
        - 役割や group の説明の写し。出どころは定義側の `description` ひとつです
        - `docs/` に入るべきページ。生成物は生成ドキュメントの役割の担当です
    """.trimIndent()
    example("README.md", "サンプルの説明")
    layout {
        "README.md".file()
    }
}
