package me.tbsten.katachi.test.architecture.sample

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.sampleGroup() = "sample".group {
    title = "サンプル"

    sampleBuild()
    sampleGuide()
}
