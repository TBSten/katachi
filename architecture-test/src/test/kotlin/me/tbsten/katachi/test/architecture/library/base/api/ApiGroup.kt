package me.tbsten.katachi.test.architecture.library.base.api

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.base.api.processor.processorGroup

fun DeclarationContainerScope.apiGroup() = "Api".group {
    title = "公開 API"

    marker()
    dsl()
    processorGroup()
    util()
}
