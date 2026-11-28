package me.tbsten.katachi.test.architecture.library.testing.architecturetest.architecturetest

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.architectureTestGroup() = "ArchitectureTest".group {
    title = "アーキテクチャテスト"

    katachiDefinition()
    konsist()
    layerImportSnapshot()
    definitionEntryPoint()
}
