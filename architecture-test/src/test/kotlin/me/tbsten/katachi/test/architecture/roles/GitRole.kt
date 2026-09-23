package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the version control settings. */
fun DeclarationContainerScope.git() = "Git" {
    title = "Git 設定"
    summary = "バージョン管理の設定ファイル"
    example(".gitignore", "生成物と手元の設定を管理対象から外す")
    layout {
        ".gitignore".file()
    }
}
