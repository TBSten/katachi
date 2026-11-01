package me.tbsten.katachi.test.architecture.plugin

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.testSourceSet

fun DeclarationContainerScope.gradlePlugin() = "GradlePlugin".role {
    title = "プラグイン本体"
    summary = "processor ごとのタスク（katachiDocs など）を登録する plugin。利用者の Gradle デーモンに読まれるので Java で書く"
    example("KatachiPlugin.java", "plugin 本体。登録キーごとに katachi<Key> タスクを登録する")
    example("KatachiProcessorTask.java", "processor を1つ決め打ちにし、test の runtimeClasspath で JVM を起動するタスク")
    example("KatachiExtension.java", "利用者が書く katachi { } ブロック")
    example("GenerateKatachiEntryPointTask.java", "architecture の参照とレジストリだけを吐くコード生成")
    layout {
        "katachi-gradle-plugin" {
            mainSourceSet / "java" / "me/tbsten/katachi/gradle" / "*.java".file()
            testSourceSet / "java" / "me/tbsten/katachi/test/gradle" / "*.java".file()
        }
    }
}
