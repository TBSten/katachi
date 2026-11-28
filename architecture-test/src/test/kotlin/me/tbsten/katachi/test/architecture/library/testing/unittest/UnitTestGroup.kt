package me.tbsten.katachi.test.architecture.library.testing.unittest

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.unitTestGroup() = "UnitTest".group {
    title = "ユニットテスト"
    description = """
        テストは層で分けていません。テストは何を import してもよいので、層で分けても強制する向きが無く、置き間違えるファイルも出ないためです。
        実在する境目は Spec（`*Spec.kt`）とその足場なので名前で切っています。
    """.trimIndent()

    spec()
    testingSupport()
}
