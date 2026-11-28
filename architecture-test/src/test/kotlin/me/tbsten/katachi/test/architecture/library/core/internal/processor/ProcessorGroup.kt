package me.tbsten.katachi.test.architecture.library.core.internal.processor

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.processorGroup() = "Processor".group {
    title = "プロセッサの内部"

    runtime()
    definitionJson()
    describeDefinition()
}
