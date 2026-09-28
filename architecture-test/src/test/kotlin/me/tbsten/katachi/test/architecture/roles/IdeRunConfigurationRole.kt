package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the run configurations IntelliJ IDEA shares through the repository. */
fun DeclarationContainerScope.ideRunConfiguration() = "IdeRunConfiguration" {
    title = "IDE の実行構成"
    summary = "IntelliJ IDEA で共有する実行構成"
    example("generateApiDocs.run.xml", "API リファレンスを作り直す Gradle タスクを IDE から走らせる")
    layout {
        ".run" / "*.run.xml".file()
    }
}
