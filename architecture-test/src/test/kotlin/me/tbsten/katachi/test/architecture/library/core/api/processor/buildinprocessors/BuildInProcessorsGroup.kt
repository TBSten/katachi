package me.tbsten.katachi.test.architecture.library.core.api.processor.buildinprocessors

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.buildInProcessorsGroup() = "BuildInProcessors".group {
    title = "組み込みの processor"
    description = """
        katachi が最初から持っている processor です。1つの定義から、検査・ドキュメント・テンプレート・差分レポートを出します。
    """.trimIndent()

    check()
    docs()
    template()
    diff()
    report()
}
