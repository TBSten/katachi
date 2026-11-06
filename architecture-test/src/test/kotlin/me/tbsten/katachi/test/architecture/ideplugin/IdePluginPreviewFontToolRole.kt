package me.tbsten.katachi.test.architecture.ideplugin

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.idePluginPreviewFontTool() = "IdePluginPreviewFontTool".role {
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
