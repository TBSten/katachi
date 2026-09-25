package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the files git itself reads. */
fun DeclarationContainerScope.git() = "Git" {
    // No `title` here on purpose: an undocumented role has no display name to show,
    // and the sample asserts that the role name is then used as-is.
    summary = ".gitignore など"
    documented = false
    description = """
        git 自身が読む設定ファイル。いまはリポジトリルートの `.gitignore` 1つだけ。

        katachi にとってもただの設定ファイルではない。既定の `files = gitTracked()` が
        `git ls-files --cached --others --exclude-standard` の結果だけを検査対象にするので、
        ここで無視されているものは最初から検査に上がってこない。各モジュールの `build/`、
        `.gradle/`、`.kotlin/`、`local.properties` に役割を書かずに済んでいるのはそのため。
        逆に `files = wholeTree()` に切り替えると、これらが軒並み `Unexpected` として出る。

        `.gitignore` に手を入れるときは、検査対象が動くことを意識する。無視するものを増やせば
        検査からも消え、減らせば役割の無いファイルとして違反になる。

        この役割だけ `title` を書いていない。表示名を持たない役割は役割名（`Git`）が
        そのまま使われる、という katachi の挙動をこのサンプルが実証するため。
    """.trimIndent()
    example(".gitignore", "git が無視するものの一覧")
    layout {
        ".gitignore".file()
    }
}
