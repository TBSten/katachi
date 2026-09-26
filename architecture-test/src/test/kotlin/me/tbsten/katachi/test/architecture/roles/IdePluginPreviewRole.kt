package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role of the IDE plugin's headless preview: the harness that renders the tool window to PNGs
 * without an IDE, and the golden PNGs `verifyPreview` compares against.
 */
fun DeclarationContainerScope.idePluginPreview() = "IdePluginPreview" {
    title = "IDE プラグインの画面プレビュー"
    summary = "Tool Window の画面を IDE なしで PNG に焼く仕組みと、verifyPreview が比べる golden の PNG"
    example("PreviewMain.kt", "シナリオとテーマごとに PNG を焼き、golden と比べる")
    example("katachi-intellij-plugin/snapshots/preview", "updatePreview が書き、verifyPreview が比べる golden")
    layout {
        "katachi-intellij-plugin" {
            "src" / "preview" / "kotlin" / "me/tbsten/katachi/intellij/preview" / "*".ktFile()
            "snapshots" / "preview" {
                "preview-*.png".file()
                // Keeps the directory in git while no scenario has been rendered yet.
                ".gitkeep".file()
            }
        }
    }
}
