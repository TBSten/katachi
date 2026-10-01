@file:OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class, ExperimentalTestApi::class)

package me.tbsten.katachi.intellij.uitest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.uiStateOf
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.newMenuDirectory
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import me.tbsten.katachi.intellij.ui.LocalStaticRendering
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogActions
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogContent
import me.tbsten.katachi.intellij.ui.dialog.PropertiesGenerateDialogStrings
import me.tbsten.katachi.intellij.uitest.dialog.DialogHost
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Verification renders (PNG, looked at by eye) of the dialog and the tool window over sample/android's real 11 templates. */
class MultiTemplateRenderTest {
    private val out = File(System.getProperty("katachi.verify.out", "build/verify-multi-template"))
    private val dispatcher = ManualDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val harness = ScenarioHarness(scope, ioDispatcher = dispatcher)
    private val android = ContractFixtures.templates("sample-android-with-captures")

    @After
    fun tearDown() = scope.cancel()

    private fun png(name: String, image: Image) {
        out.mkdirs()
        File(out, name).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    private fun click(scene: ImageComposeScene, at: Offset) {
        scene.sendPointerEvent(PointerEventType.Move, at)
        scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
    }

    private fun dialogPng(name: String, host: DialogHost, openTag: String?, dark: Boolean = false, width: Int = 640, height: Int = 520) {
        val noActions = object : GenerateDialogActions {
            override fun onSelectTemplate(index: Int) = Unit
            override fun onSelectDefinition(index: Int) = Unit
            override fun onInput(name: String, value: String) = Unit
            override fun onGenerate() = Unit
        }
        val scene = ImageComposeScene(width, height, Density(1f)) {
            IntUiTheme(isDark = dark) {
                CompositionLocalProvider(LocalStaticRendering provides true) {
                    Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) { GenerateDialogContent(host.ui, host.strings, noActions) }
                }
            }
        }
        try {
            var now = 0L
            fun frame() = scene.render(now.also { now += 16_666_667L })
            repeat(3) { frame() }
            if (openTag != null) {
                var target: Offset? = null
                fun walk(node: androidx.compose.ui.semantics.SemanticsNode) {
                    if (node.config.getOrNull(SemanticsProperties.TestTag) == openTag) {
                        target = Offset(node.positionInRoot.x + node.size.width / 2f, node.positionInRoot.y + node.size.height / 2f)
                    }
                    node.children.forEach(::walk)
                }
                scene.semanticsOwners.forEach { walk(it.unmergedRootSemanticsNode) }
                click(scene, checkNotNull(target) { "no $openTag" })
                repeat(4) { frame() }
            }
            png(name, frame())
        } finally {
            scene.close()
        }
    }

    @Test
    fun `dialog PNG 通知から開いた sample-android のテンプレートのセレクトボックス`() {
        val templates = android.map { ModuleTemplate(module(":arch-a"), it) }
        val user = templates.first { it.id.template == "data.Repository.user" }
        val host = DialogHost(templates, initial = user, seeds = mapOf("name" to "Profile"), origin = newMenuDirectory("data/src/main/kotlin/com/example/sample/data/user"))
        dialogPng("dialog-closed.png", host, null)
        // Opening the select box with all 11 templates: two of them are titled "settings".
        try {
            dialogPng("dialog-template-select-open.png", host, KatachiTestTags.DIALOG_TEMPLATE)
            File(out, "dialog-open-crash.txt").writeText("no crash")
        } catch (e: Throwable) {
            File(out, "dialog-open-crash.txt").writeText(e.stackTraceToString().lines().take(12).joinToString("\n"))
        }
        File(out, "dialog-options.txt").writeText(host.ui.templateOptions.joinToString("\n"))
        // Only the four titles of data.Repository (no duplicates): the popup as it looks when it does open.
        val repos = templates.filter { it.id.template.startsWith("data.Repository") }
        val hostRepos = DialogHost(repos, initial = repos.first(), seeds = mapOf("name" to "Profile"), origin = newMenuDirectory("data/src/main/kotlin/com/example/sample/data/user"))
        dialogPng("dialog-template-select-open-unique.png", hostRepos, KatachiTestTags.DIALOG_TEMPLATE)
        dialogPng("dialog-template-select-open-unique-dark.png", hostRepos, KatachiTestTags.DIALOG_TEMPLATE, dark = true)
        val both = templates + android.map { ModuleTemplate(module(":arch-b"), it) }
        val host2 = DialogHost(both, initial = user, seeds = mapOf("name" to "Profile"), origin = newMenuDirectory("data/src/main/kotlin/com/example/sample/data/user"))
        dialogPng("dialog-two-definitions.png", host2, null, height = 460)
        assertEquals(11, host.ui.templateOptions.size)
    }

    private fun ui() = uiStateOf(harness.vm.state.value, JapaneseKatachiStrings, ScenarioHarness.NOW)

    private fun dispatch(vararg intents: KatachiIntent) {
        intents.forEach(harness.vm::dispatch)
        dispatcher.runAll()
    }

    @Test
    fun `tool window PNG sample-android を読み込んで id 違いの役割の行を強調する`() = runComposeUiTest {
        harness.loadJson = ContractFixtures.json("sample-android-with-captures")
        setContent {
            IntUiTheme {
                Box(Modifier.requiredSize(360.dp, 560.dp).background(JewelTheme.globalColors.panelBackground)) {
                    val state by harness.vm.state.collectAsState()
                    KatachiToolWindowContent(uiStateOf(state, JapaneseKatachiStrings, ScenarioHarness.NOW), harness.vm::dispatch)
                }
            }
        }
        dispatch(KatachiIntent.Opened)
        waitForIdle()
        png("toolwindow-loaded.png", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()))
        val ids = android.map { it.template }
        for ((label, template) in listOf("userImpl" to "data.Repository.userImpl", "settings-repo" to "data.Repository.settings", "settings-test" to "feature.FeatureTest.settings")) {
            val id = harness.state.snapshots.flatMap { s -> s.templates.map { s.module to it } }.first { it.second.template == template }.let { (m, t) -> me.tbsten.katachi.intellij.model.TemplateId(m.id, t.template) }
            dispatch(KatachiIntent.RevealTemplate(id))
            waitForIdle()
            png("toolwindow-reveal-$label.png", Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()))
            val highlighted = onNodeWithTag(KatachiTestTags.HIGHLIGHTED)
            highlighted.assertExists()
        }
        assertEquals(11, ids.size)
    }
}
