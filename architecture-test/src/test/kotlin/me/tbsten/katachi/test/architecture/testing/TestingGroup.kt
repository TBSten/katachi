package me.tbsten.katachi.test.architecture.testing

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "テスト"

    spec()
    specSupport()
    architectureDefinition()
    docsE2eTest()
    docsUnitTest()
}
