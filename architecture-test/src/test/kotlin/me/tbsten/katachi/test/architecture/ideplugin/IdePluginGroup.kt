package me.tbsten.katachi.test.architecture.ideplugin

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.idePluginGroup() = "ide-plugin".group {
    title = "IDE プラグイン"

    idePluginBuild()
    idePluginSource()
    idePluginTest()
    idePluginPreview()
    idePluginPreviewFont()
    idePluginPreviewFontTool()
}
