package me.tbsten.katachi.test.architecture.ideplugin

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist

fun DeclarationContainerScope.idePluginSource() = "IdePluginSource".role {
    title = "IDE プラグインの実装"
    summary = "IDE に触る側（src/main）と、plugin 本体・preview・uiTest の全部でコンパイルするプラットフォーム非依存の側（src/shared）"
    description = """
        `src/main/kotlin` は IDE の SDK に触る側（ツールウィンドウの登録、ガター、通知、設定）です。
        `src/shared/kotlin` はプラットフォーム非依存の側（モデル、データ層、ViewModel と UI 状態、Compose (Jewel) の画面）で、
        plugin 本体（IDE 同梱の Jewel）と headless preview（standalone Jewel）と uiTest の全部でコンパイルします。
        そのため preview は、出荷されるコードそのものを描きます。
        preview と uiTest は `src/shared` を直接読まず、Gradle の sync タスクが build/generated に書く写しを読みます
        （IntelliJ は 1 つのディレクトリを 1 つのモジュールにしか置けないため）。
    """.trimIndent()
    example("KatachiToolWindowFactory.kt", "Tool Window に Compose の画面を載せる")
    example("KatachiToolWindowContent.kt", "Tool Window の画面。preview と共有する")
    layout {
        "katachi-intellij-plugin" / "src" {
            "main" / "kotlin" / "me/tbsten/katachi/intellij" {
                sdkCallsAreGuarded()
                "**" / "*".ktFile()
            }
            "main" / "resources" / "META-INF" / "plugin.xml".file()
            // The MCP tools, read only where the JetBrains MCP Server is present (`<depends optional="true">`).
            "main" / "resources" / "META-INF" / "katachi-mcp.xml".file()
            "main" / "resources" / "META-INF" / "pluginIcon.svg".file()
            "main" / "resources" / "META-INF" / "pluginIcon_dark.svg".file()
            "main" / "resources" / "icons" / "*.svg".file()
            // The 12×12 icons of the source files' gutter (ide-gutter), apart from the tool window's 13×13 ones.
            "main" / "resources" / "icons" / "gutter" / "*.svg".file()
            "main" / "resources" / "messages" / "KatachiBundle.properties".file()
            "main" / "resources" / "messages" / "KatachiBundle_*.properties".file()
            "shared" / "kotlin" / "me/tbsten/katachi/intellij" / "**" / "*".ktFile()
        }
    }
}

private fun LayoutScope.sdkCallsAreGuarded() =
    "IntelliJ SDK を呼ぶファイルは sdkCall で囲うこと".konsist {
        files.mustNot(::callsSdkUnguarded)
    }

private val FILES_WITHOUT_SDK_CALLS: Map<String, String> = mapOf(
    "SdkCalls" to "defines sdkCall",
    "KatachiBundle" to "lookups of the plugin's own keys; a missing key shows as !key!, it does not throw",
    "KatachiSettings" to "persisted state; every caller calls getInstance() inside sdkCall",
    "KatachiDebugBridge" to "driven by the Driver smoke only, where an exception is meant to reach the test",
    "EntryGenerationSupport" to "packageOfDirectory is called only inside sdkCall { readAction { } } (IdeEffectsImpl); the rest is text",
    "KatachiNotificationPanel" to "builds Swing components; its only caller wraps the build in sdkCall (KatachiEditorNotificationProvider)",
    "KatachiToolWindowViewModel" to "calls nothing of the SDK but its Logger",
    "DefinitionModelService" to "an interface: types and signatures, no SDK call; the implementation wraps its calls in sdkCall",
    "ResolvedSite" to "a result type that holds a VirtualFile; no SDK call; the implementation that finds it wraps its calls in sdkCall",
    "ViolationsService" to "an interface: types and signatures, no SDK call; the implementation wraps its calls in sdkCall",
    "GutterActions" to "runs the foundation's actions; its callers (the gutter menu's row, the quick fix) call it inside sdkCall",
    "AnnotationRanges" to "reads the PSI's declarations and the Document; its only caller (the violation annotator's doAnnotate) wraps the call in sdkCall",
    "ViolationAnnotation" to "types that hold a PsiFile, a VirtualFile and a TextRange; no SDK call",
    "KatachiDeclarationLineMarkerProvider" to "hands the daemon's elements to GutterMarkerDrawing, which wraps every SDK call in sdkCall",
    "KatachiFileLineMarkerProvider" to "hands the daemon's elements to GutterMarkerDrawing, which wraps every SDK call in sdkCall",
    "NoRoleDecider" to "reads through RulesPorts and the definition service, whose implementations wrap their SDK calls in sdkCall; both callers, the two providers (NoRoleNotificationProvider and KatachiEditorNotificationProvider), wrap the whole decision in sdkCall",
    "NoRoleNotificationPanel" to "builds Swing components; its only caller wraps the build in sdkCall (NoRoleNotificationProvider)",
    "AnchorShapes" to "reads the shape of a call from the PSI alone (no resolve, no index); its only caller, DefinitionAnchors, wraps the call in sdkCall",
    "AuthoringPorts" to "a project service holding a lambda that finds the definition service; the features that call it (completion, debug reader) wrap their use in sdkCall",
    "DefinitionErrorAnnotation" to "types that hold a PsiFile, a VirtualFile and a TextRange; no SDK call",
    "KatachiCodeVisionGroup" to "returns constant strings for the settings page; no SDK call",
    "NoRolePanels" to "a weak map of editors to panels; no SDK call, only SDK types as keys",
    "KatachiMcpToolset" to "the MCP Server's entry: its `call` catches every failure of the tools itself and turns it into mcpFail (which must reach the server, so sdkCall would swallow it); the SDK calls behind it are in KatachiMcpTools' collaborators, inside sdkCall",
)

private fun callsSdkUnguarded(file: KoFileDeclaration): Boolean =
    file.name !in FILES_WITHOUT_SDK_CALLS &&
        file.imports.any { it.name.startsWith("com.intellij.") } &&
        "sdkCall" !in file.text
