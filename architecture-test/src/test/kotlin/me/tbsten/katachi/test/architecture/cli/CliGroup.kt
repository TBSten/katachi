package me.tbsten.katachi.test.architecture.cli

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.cliGroup() = "cli".group {
    title = "katachi コマンド"
    description = """
        `brew install tbsten/tap/katachi` で入る `katachi` コマンド。入口は Kotlin スクリプト 1 枚で、中身は Maven Central の `katachi-script` から引きます。
        Gradle プラグインと違って利用者のビルドの外で動くので、別の group にしています。
    """.trimIndent()

    script()
    cliKit()
}
