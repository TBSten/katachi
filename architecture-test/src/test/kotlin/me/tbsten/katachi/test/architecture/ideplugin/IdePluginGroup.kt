package me.tbsten.katachi.test.architecture.ideplugin

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.idePluginGroup() = "ide-plugin".group {
    title = "IDE プラグイン"
    description = """
        `sample/` のように `ignore()` せず、中身を宣言しています。
        IDE プラグインは katachi で自分自身を宣言しておらず、迷い込んだファイルをほかに見つける仕組みが無いためです。
        role は build・sources・tests・preview と粗く分けていて、コードが増えてから層に分けます。
    """.trimIndent()

    idePluginBuild()
    idePluginSource()
    idePluginTest()
    idePluginPreview()
    idePluginPreviewFont()
    idePluginPreviewFontTool()
}
