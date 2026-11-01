package me.tbsten.katachi.test.architecture.ideplugin

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The roles of `katachi-intellij-plugin/`, the IntelliJ IDEA / Android Studio plugin.
 *
 * Declared file by file rather than ignored like `sample/`: unlike a sample, the plugin does not
 * describe itself with katachi, so nothing else would notice a stray file in it.
 *
 * It is an independent Gradle build (see `IdePluginBuild`), so none of its paths go through
 * module resolution: no module path of this build resolves to it. The roles are deliberately coarse —
 * build, sources, tests, preview — until the plugin has enough code to be worth layering.
 */
fun DeclarationContainerScope.idePluginGroup() = "ide-plugin".group {
    title = "IDE プラグイン"

    idePluginBuild()
    idePluginSource()
    idePluginTest()
    idePluginPreview()
    idePluginPreviewFont()
    idePluginPreviewFontTool()
}
