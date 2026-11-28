package me.tbsten.katachi.test.architecture.library.core

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.core.api.apiGroup
import me.tbsten.katachi.test.architecture.library.core.internal.internalGroup

fun DeclarationContainerScope.libraryCoreGroup() = "LibraryCore".group {
    title = "ライブラリ本体 (:katachi)"
    description = """
        このリポジトリでは「ファイルの種類」と「パッケージの層」がたまたま一致するので、層を role にしています。
        `:katachi` の公開する面は `Api`、公開しない面は `Internal` に分けています。
    """.trimIndent()

    apiGroup()
    internalGroup()
}
