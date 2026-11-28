package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.git() = "Git".role {
    title = "Git 設定"
    summary = "バージョン管理の設定ファイル"
    example(".gitignore", "生成物と手元の設定を管理対象から外す")
    example(".gitattributes", "Windows 用 .cmd の改行を CRLF に固定する")
    layout {
        ".gitignore".file()
        ".gitattributes".file()
    }
}
