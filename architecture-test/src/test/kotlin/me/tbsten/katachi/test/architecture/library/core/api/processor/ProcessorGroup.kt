package me.tbsten.katachi.test.architecture.library.core.api.processor

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.core.api.processor.buildinprocessors.buildInProcessorsGroup

fun DeclarationContainerScope.processorGroup() = "Processor".group {
    title = "プロセッサ"

    core()
    buildInProcessorsGroup()
}
