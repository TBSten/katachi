package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div

/**
 * The role of the scripts that rebuild [idePluginPreviewFont]: collect the characters the previews
 * use, cut Noto Sans JP down to them and match its vertical metrics to Inter. Run them when a
 * preview starts using a character the bundled font does not have (the preview gate names it).
 */
fun DeclarationContainerScope.idePluginPreviewFontTool() = "IdePluginPreviewFontTool" {
    title = "IDE プラグインのプレビューのフォントを作り直すスクリプト"
    summary = "プレビューで使う文字を集め、Noto Sans JP を切り詰めて Inter の行の高さに合わせるスクリプト"
    example("build_font.sh", "文字を集めてからフォントを作り直す入口")
    layout {
        "katachi-intellij-plugin" / "scripts" / "preview-font" {
            "*.py".file()
            "*.sh".file()
            // The characters collect_chars.py found, kept so a rebuild gives the same bytes.
            "chars.txt".file()
        }
    }
}
