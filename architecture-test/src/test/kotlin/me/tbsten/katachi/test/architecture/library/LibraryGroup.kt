package me.tbsten.katachi.test.architecture.library

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.libraryGroup() = "library".group {
    title = "ライブラリ"

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
