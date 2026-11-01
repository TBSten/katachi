package me.tbsten.katachi.test.architecture.plugin

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.pluginGroup() = "plugin".group {
    title = "Gradle プラグイン"
    description = """
        `library` の 1 層ではなく、独立した group にしています。
        利用者の Gradle デーモンに読まれる、Java で書く、Gradle 8.0 以上という互換範囲を持つ、の 3 点がほかのモジュールと違うためです。
    """.trimIndent()

    gradlePlugin()
}
