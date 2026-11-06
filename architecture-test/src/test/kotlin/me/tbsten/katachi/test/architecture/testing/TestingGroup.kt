package me.tbsten.katachi.test.architecture.testing

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "テスト"
    description = """
        テストは層で分けていません。テストは何を import してもよいので、層で分けても強制する向きが無く、置き間違えるファイルも出ないためです。
        実在する境目は Spec（`*Spec.kt`）とその足場なので名前で切り、定義そのものは Spec の一部にせず別の role にしています。
    """.trimIndent()

    spec()
    specSupport()
    architectureDefinitionEntry()
    groupDefinition()
    roleDefinition()
    definitionSupport()
    definitionEntryPoint()
    layerImportSnapshot()
    docsE2eTest()
    docsUnitTest()
}
