package me.tbsten.katachi.test.architecture.library

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.libraryGroup() = "library".group {
    title = "ライブラリ"
    description = """
        このリポジトリでは「ファイルの種類」と「パッケージの層」がたまたま一致するので、role を層にしています。
        ずれるのは `Marker`（どの層にも属さないルートパッケージ）と `Dsl`（種類の違う `dsl.gradle`・`dsl.kotlin`・`dsl.files` を同じ層として含む）で、扱いはそれぞれの role に書いています。
    """.trimIndent()

    marker()
    util()
    dsl()
    processor()
    check()
    report()
    docs()
    template()
    ide()
}
