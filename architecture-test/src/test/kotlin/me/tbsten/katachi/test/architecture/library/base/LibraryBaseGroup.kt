package me.tbsten.katachi.test.architecture.library.base

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.base.api.apiGroup
import me.tbsten.katachi.test.architecture.library.base.internal.internalGroup

fun DeclarationContainerScope.libraryBaseGroup() = "LibraryBase".group {
    title = "ライブラリ本体 (:katachi)"
    description = """
        このリポジトリでは「ファイルの種類」と「パッケージの層」がたまたま一致するので、層を role にしています。
        `:katachi` の公開する面は `Api`、公開しない面は `Internal` に分けています。
    """.trimIndent()

    apiGroup()
    internalGroup()
}
