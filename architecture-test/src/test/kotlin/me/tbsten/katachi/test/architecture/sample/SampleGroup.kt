package me.tbsten.katachi.test.architecture.sample

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.sampleGroup() = "Sample".group {
    title = "サンプル"

    sampleBuild()
    sampleGuide()
}
