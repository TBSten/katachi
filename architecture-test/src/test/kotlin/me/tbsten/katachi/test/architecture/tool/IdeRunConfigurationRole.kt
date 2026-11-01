package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.ideRunConfiguration() = "IdeRunConfiguration".role {
    title = "IDE の実行構成"
    summary = "IntelliJ IDEA で共有する実行構成"
    example("generateApiDocs.run.xml", "API リファレンスを作り直す Gradle タスクを IDE から走らせる")
    layout {
        ".run" / "*.run.xml".file()
    }
}
