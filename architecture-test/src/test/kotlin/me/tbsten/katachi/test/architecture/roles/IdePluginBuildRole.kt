package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.kotlin.ktsFile

/**
 * The role of the IDE plugin's build: an independent Gradle build with its own wrapper.
 *
 * Independent because one Gradle build cannot mix two versions of the Kotlin Gradle Plugin, and
 * the plugin has to be compiled with a Kotlin no newer than the one bundled in its target IDE.
 * So it is written as a plain directory, the same way `BuildLogic` writes `buildSrc`.
 */
fun DeclarationContainerScope.idePluginBuild() = "IdePluginBuild" {
    title = "IDE プラグインのビルド"
    summary = "IDE プラグインの独立した Gradle ビルド。対象の IDE に同梱された Kotlin・Jewel に版を揃える"
    example("katachi-intellij-plugin/build.gradle.kts", "IntelliJ Platform Gradle Plugin の配線と、preview のタスク")
    example("katachi-intellij-plugin/gradle/libs.versions.toml", "対象の IDE（build 261）に揃えた版の塊")
    layout {
        "katachi-intellij-plugin" {
            "build.gradle".ktsFile()
            "settings.gradle".ktsFile()
            "gradle.properties".file()
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
