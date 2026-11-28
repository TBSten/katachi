package me.tbsten.katachi.test.architecture.library.base.api.processor

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.base.api.processor.buildinprocessors.buildInProcessorsGroup

fun DeclarationContainerScope.processorGroup() = "Processor".group {
    title = "プロセッサ"

    core()
    buildInProcessorsGroup()
}
