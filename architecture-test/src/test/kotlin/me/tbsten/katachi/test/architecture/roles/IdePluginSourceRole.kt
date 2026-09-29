package me.tbsten.katachi.test.architecture.roles

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist

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
            "main" / "kotlin" / "me/tbsten/katachi/intellij" {
                sdkCallsAreGuarded()
                "**" / "*".ktFile()
            }
            "main" / "resources" / "META-INF" / "plugin.xml".file()
            "main" / "resources" / "META-INF" / "pluginIcon.svg".file()
            "main" / "resources" / "META-INF" / "pluginIcon_dark.svg".file()
            "main" / "resources" / "icons" / "*.svg".file()
            "main" / "resources" / "messages" / "KatachiBundle.properties".file()
            "main" / "resources" / "messages" / "KatachiBundle_*.properties".file()
            "shared" / "kotlin" / "me/tbsten/katachi/intellij" / "**" / "*".ktFile()
        }
    }
}

/**
 * The plugin calls into the IntelliJ Platform SDK only through its `sdkCall { }`
 * (`katachi-intellij-plugin/.../ide/SdkCalls.kt`), so that what the platform throws reaches the
 * user as a state of the screen and not as a red IDE error.
 *
 * Checked per file: a file importing `com.intellij.` must use `sdkCall` somewhere. It cannot see a
 * single call left bare inside a file that uses it elsewhere (that takes type resolution); what it
 * catches is the likelier slip, a new file written without the guard at all.
 */
private fun LayoutScope.sdkCallsAreGuarded() =
    "IntelliJ SDK を呼ぶファイルは sdkCall で囲うこと".konsist {
        files.mustNot(::callsSdkUnguarded)
    }

/**
 * Files that import `com.intellij.` and may do without `sdkCall`, with why. A file goes here only
 * when none of its SDK calls can fail in a way the user should see, or its callers guard it.
 */
private val FILES_WITHOUT_SDK_CALLS: Map<String, String> = mapOf(
    "SdkCalls" to "defines sdkCall",
    "KatachiBundle" to "lookups of the plugin's own keys; a missing key shows as !key!, it does not throw",
    "KatachiSettings" to "persisted state; every caller calls getInstance() inside sdkCall",
    "KatachiDebugBridge" to "driven by the Driver smoke only, where an exception is meant to reach the test",
    "EntryGenerationSupport" to "packageOfDirectory is called only inside sdkCall { readAction { } } (IdeEffectsImpl); the rest is text",
    "KatachiNotificationPanel" to "builds Swing components; its only caller wraps the build in sdkCall (KatachiEditorNotificationProvider)",
    "KatachiToolWindowViewModel" to "calls nothing of the SDK but its Logger",
)

/** Whether [file] imports `com.intellij.` and never says `sdkCall`, without a reason in [FILES_WITHOUT_SDK_CALLS]. */
private fun callsSdkUnguarded(file: KoFileDeclaration): Boolean =
    file.name !in FILES_WITHOUT_SDK_CALLS &&
        file.imports.any { it.name.startsWith("com.intellij.") } &&
        "sdkCall" !in file.text
