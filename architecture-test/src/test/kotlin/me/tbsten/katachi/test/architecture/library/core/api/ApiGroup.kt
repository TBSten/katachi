package me.tbsten.katachi.test.architecture.library.core.api

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.core.api.processor.processorGroup

fun DeclarationContainerScope.apiGroup() = "Api".group {
    title = "公開 API"

    marker()
    exceptions()
    dsl()
    processorGroup()
    util()
}
