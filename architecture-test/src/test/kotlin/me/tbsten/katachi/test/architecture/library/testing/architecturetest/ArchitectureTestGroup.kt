package me.tbsten.katachi.test.architecture.library.testing.architecturetest

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.testing.architecturetest.architecturetest.architectureTestGroup
import me.tbsten.katachi.test.architecture.library.testing.architecturetest.architecturedefinition.architectureDefinitionGroup

fun DeclarationContainerScope.architectureTestOuterGroup() = "ArchitectureTest".group {
    title = "アーキテクチャテスト"

    architectureTestGroup()
    architectureDefinitionGroup()
}
