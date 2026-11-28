package me.tbsten.katachi.test.architecture.library

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.core.libraryCoreGroup
import me.tbsten.katachi.test.architecture.library.integrations.integrationsGroup
import me.tbsten.katachi.test.architecture.library.testing.testingGroup

fun DeclarationContainerScope.libraryGroup() = "Library".group {
    title = "ライブラリ"

    libraryCoreGroup()
    integrationsGroup()
    testingGroup()
}
