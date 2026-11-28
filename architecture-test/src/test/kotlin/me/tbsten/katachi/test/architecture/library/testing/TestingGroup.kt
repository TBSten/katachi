package me.tbsten.katachi.test.architecture.library.testing

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.testing.unittest.unitTestGroup
import me.tbsten.katachi.test.architecture.library.testing.architecturetest.architectureTestOuterGroup

fun DeclarationContainerScope.testingGroup() = "Testing".group {
    title = "テスト"

    unitTestGroup()
    architectureTestOuterGroup()
    docsTest()
    benchmarks()
}
