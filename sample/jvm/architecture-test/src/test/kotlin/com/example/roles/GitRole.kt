package com.example.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the version control configuration. */
fun DeclarationContainerScope.git() = "Git" {
    title = "Git 設定"
    summary = "バージョン管理の設定ファイル"
    example(".gitignore", "生成物を管理対象から外す")
    layout {
        ".gitignore".file()
    }
}
