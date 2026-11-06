package me.tbsten.katachi.test.architecture.ideplugin

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.idePluginPreviewFont() = "IdePluginPreviewFont".role {
    title = "IDE プラグインのプレビューの日本語フォント"
    summary = "プレビューが日本語を描く Noto Sans JP（プレビューで使う文字だけに切り詰めたもの）と、そのライセンス"
    description = """
        OS の日本語フォントは macOS の版によって背が高く、CI でだけ `verifyPreview` が文字切れを報告しました。
        フォントを同梱して、プレビューが実行環境のフォントに依存しないようにしています。
    """.trimIndent()
    example("NotoSansJP-Regular.ttf", "build_font.sh が切り詰め、行の高さを Inter に合わせたもの")
    layout {
        "katachi-intellij-plugin" / "src" / "preview" / "resources" / "fonts" / "noto-sans-jp" {
            "NotoSansJP-*.ttf".file()
            "OFL.txt".file()
        }
    }
}
