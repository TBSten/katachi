package me.tbsten.katachi.test.architecture.library

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.base.libraryBaseGroup
import me.tbsten.katachi.test.architecture.library.integrations.integrationsGroup
import me.tbsten.katachi.test.architecture.library.testing.testingGroup

fun DeclarationContainerScope.libraryGroup() = "Library".group {
    title = "ライブラリ"

    libraryBaseGroup()
    integrationsGroup()
    testingGroup()
}
