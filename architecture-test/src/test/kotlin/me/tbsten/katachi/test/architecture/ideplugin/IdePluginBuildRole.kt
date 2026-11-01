package me.tbsten.katachi.test.architecture.ideplugin

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.kotlin.ktsFile

fun DeclarationContainerScope.idePluginBuild() = "IdePluginBuild".role {
    title = "IDE プラグインのビルド"
    summary = "IDE プラグインの独立した Gradle ビルド。対象の IDE に同梱された Kotlin・Jewel に版を揃える"
    example("katachi-intellij-plugin/build.gradle.kts", "IntelliJ Platform Gradle Plugin の配線と、preview のタスク")
    example("katachi-intellij-plugin/gradle/libs.versions.toml", "対象の IDE（build 261）に揃えた版の塊")
    layout {
        "katachi-intellij-plugin" {
            "build.gradle".ktsFile()
            "settings.gradle".ktsFile()
            "gradle.properties".file()
            "verifyPlugin-ignored-problems.txt".file()
            "gradlew".file()
            "gradlew.bat".file()
            "gradle" {
                "libs.versions.toml".file()
                "wrapper" / "gradle-wrapper.jar".file()
                "wrapper" / "gradle-wrapper.properties".file()
            }
        }
    }
}
