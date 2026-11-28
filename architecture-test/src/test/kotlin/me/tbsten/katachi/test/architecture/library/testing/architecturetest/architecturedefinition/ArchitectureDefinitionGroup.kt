package me.tbsten.katachi.test.architecture.library.testing.architecturetest.architecturedefinition

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.architectureDefinitionGroup() = "ArchitectureDefinition".group {
    title = "アーキテクチャ定義の型"

    constants()
    groupDefinition()
    roleDefinition()
}
