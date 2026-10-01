package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div

/**
 * The role of the Japanese font the IDE plugin's headless preview draws with: Noto Sans JP, cut
 * down to the characters the previews use, with its licence beside it. Bundled so that the preview
 * does not depend on the fonts of the machine it runs on (the OS's own Japanese font is taller on
 * some macOS versions, which made `verifyPreview` report text as cut on CI only).
 */
fun DeclarationContainerScope.idePluginPreviewFont() = "IdePluginPreviewFont" {
    title = "IDE プラグインのプレビューの日本語フォント"
    summary = "プレビューが日本語を描く Noto Sans JP（プレビューで使う文字だけに切り詰めたもの）と、そのライセンス"
    example("NotoSansJP-Regular.ttf", "build_font.sh が切り詰め、行の高さを Inter に合わせたもの")
    layout {
        "katachi-intellij-plugin" / "src" / "preview" / "resources" / "fonts" / "noto-sans-jp" {
            "NotoSansJP-*.ttf".file()
            "OFL.txt".file()
        }
    }
}
