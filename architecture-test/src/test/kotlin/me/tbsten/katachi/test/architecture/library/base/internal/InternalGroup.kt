package me.tbsten.katachi.test.architecture.library.base.internal

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.base.internal.processor.processorGroup

fun DeclarationContainerScope.internalGroup() = "Internal".group {
    title = "内部の実装"

    processorGroup()
    util()
}
