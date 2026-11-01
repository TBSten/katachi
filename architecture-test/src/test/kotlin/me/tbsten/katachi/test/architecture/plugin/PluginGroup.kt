package me.tbsten.katachi.test.architecture.plugin

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.pluginGroup() = "plugin".group {
    title = "Gradle プラグイン"

    gradlePlugin()
}
