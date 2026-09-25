package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the prose that explains this sample to whoever opens it. */
fun DeclarationContainerScope.documentation() = "Documentation" {
    title = "ドキュメント"
    summary = "README.md など、リポジトリを読む人に向けた説明"
    documented = false
    description = """
        このサンプルを開いた人に向けた散文。いまはルートの `README.md` 1つで、
        どういうサンプルか、読むべきファイル、どのコマンドで何が走るかが書いてある。

        役割の一覧やレイヤーの説明はここに書かない。それは定義そのものが持っているもので、
        写しを置けば必ず片方が古くなる。`README.md` が引き受けるのは、定義からは出てこないこと
        （このサンプルの狙い、どこから読むか、検査の回し方）に限る。

        `documented = false`。読む人に向けた説明という役割は、このアプリが何であるかを
        説明しないので、生成されるドキュメントには出さない。検査はするので、
        `README.md` を消したり名前を変えたりすれば違反になる。
    """.trimIndent()
    example("README.md", "サンプルの説明")
    layout {
        "README.md".file()
    }
}
