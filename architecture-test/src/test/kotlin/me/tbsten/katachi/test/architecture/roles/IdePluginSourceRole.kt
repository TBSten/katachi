package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role of the IDE plugin's production code.
 *
 * `src/shared/kotlin` holds the UI Composables, compiled both into the plugin (against the IDE's
 * bundled Jewel) and into the headless preview (against standalone Jewel), so that the preview
 * renders the code that ships.
 */
fun DeclarationContainerScope.idePluginSource() = "IdePluginSource" {
    title = "IDE プラグインの実装"
    summary = "Tool Window の登録と、plugin 本体と preview の両方でコンパイルする Compose (Jewel) の画面"
    example("KatachiToolWindowFactory.kt", "Tool Window に Compose の画面を載せる")
    example("KatachiToolWindowContent.kt", "Tool Window の画面。preview と共有する")
    layout {
        "katachi-intellij-plugin" / "src" {
            "main" / "kotlin" / "me/tbsten/katachi/intellij" / "**" / "*".ktFile()
            "main" / "resources" / "META-INF" / "plugin.xml".file()
            "shared" / "kotlin" / "me/tbsten/katachi/intellij" / "**" / "*".ktFile()
        }
    }
}
