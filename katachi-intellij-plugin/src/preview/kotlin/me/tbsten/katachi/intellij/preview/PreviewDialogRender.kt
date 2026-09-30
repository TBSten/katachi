@file:OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)

package me.tbsten.katachi.intellij.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.LocalStaticRendering
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogActions
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogContent
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogStrings
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogUiState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.skia.EncodedImageFormat

/** What one dialog scenario drew: the PNG and where every part ended up. */
internal class DialogRender(val png: ByteArray, val nodes: List<PreviewChecks.LayoutNode>)

private const val FRAME_NANOS = 16_666_667L

private val noActions = object : GenerateDialogActions {
    override fun onSelectTemplate(index: Int) = Unit

    override fun onSelectDefinition(index: Int) = Unit

    override fun onInput(name: String, value: String) = Unit

    override fun onGenerate() = Unit
}

/**
 * Draws [scenario] on standalone Compose the way the preview draws the tool window, but through an
 * [ImageComposeScene] so that it can send a click (to open a select box), draw several frames (the
 * cursor moves to the first empty field) and read the semantics (where every part is, for the
 * overflow and cut-off gate).
 */
internal fun renderDialog(scenario: DialogScenario, dark: Boolean): DialogRender {
    val strings = if (scenario.english) englishDialogStrings else japaneseDialogStrings
    val scene = ImageComposeScene(scenario.size.width, scenario.size.height, Density(1f)) {
        IntUiTheme(isDark = dark) {
            CompositionLocalProvider(LocalStaticRendering provides true) {
                Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
                    DialogFrame(scenario.ui, strings, scenario.english)
                }
            }
        }
    }
    try {
        var now = 0L
        fun frame() = scene.render(now.also { now += FRAME_NANOS })
        repeat(2) { frame() }
        // A window that has the focus: a click on the margin gives it to the scene, and the cursor
        // then moves to the first empty field the way it does when the dialog opens.
        click(scene, Offset(2f, 2f))
        repeat(4) { frame() }
        scenario.open?.let { which ->
            val tag = if (which == OpenSelect.Template) KatachiTestTags.DIALOG_TEMPLATE else KatachiTestTags.DIALOG_DEFINITION
            val target = checkNotNull(nodesOf(scene).firstOrNull { it.tag == tag }) { "no select box tagged $tag in ${scenario.name}" }
            click(scene, target.center)
            repeat(3) { frame() }
        }
        val image = frame()
        return DialogRender(image.encodeToData(EncodedImageFormat.PNG)!!.bytes, layoutNodesOf(scene))
    } finally {
        scene.close()
    }
}

/** The dialog body as `DialogWrapper` will show it: the content above the buttons. */
@Composable
private fun DialogFrame(ui: GenerateDialogUiState, strings: GenerateDialogStrings, english: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { GenerateDialogContent(ui, strings, noActions) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = {}) { Text(if (english) "Cancel" else "キャンセル") }
            Box(Modifier.padding(start = 8.dp)) {
                DefaultButton(onClick = {}, enabled = ui.canGenerate) { Text(strings.generate) }
            }
        }
    }
}

private fun click(scene: ImageComposeScene, at: Offset) {
    scene.sendPointerEvent(PointerEventType.Move, at)
    scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
    scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
}

/** One node of the unmerged semantics tree with what the gates need of it. */
private class SeenNode(
    val tag: String?,
    val text: String?,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val insideForm: Boolean,
    val overlay: Boolean,
    val cut: Boolean,
    val cutDetail: String,
) {
    val center: Offset get() = Offset((left + right) / 2, (top + bottom) / 2)
}

private fun nodesOf(scene: ImageComposeScene): List<SeenNode> {
    val seen = mutableListOf<SeenNode>()

    fun walk(node: SemanticsNode, insideForm: Boolean, overlay: Boolean) {
        val config = node.config
        val tag = config.getOrNull(SemanticsProperties.TestTag)
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        val editable = config.getOrNull(SemanticsProperties.EditableText) != null
        val layouts = mutableListOf<TextLayoutResult>()
        // The text of an editable field scrolls inside it by design; only plain text can be cut.
        if (!editable) config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val position = node.positionInRoot
        seen += SeenNode(
            tag = tag,
            text = text,
            left = position.x,
            top = position.y,
            right = position.x + node.size.width,
            bottom = position.y + node.size.height,
            insideForm = insideForm,
            overlay = overlay,
            cut = layouts.any { it.hasVisualOverflow },
            cutDetail = layouts.filter { it.hasVisualOverflow }.joinToString("; ") {
                "text ${it.size.width}x${it.size.height} in box ${node.size.width}x${node.size.height}, ${it.lineCount} lines, " +
                    "font ${it.layoutInput.style.fontFamily} ${it.layoutInput.style.fontSize}"
            },
        )
        val form = insideForm || tag == KatachiTestTags.DIALOG_FORM
        node.children.forEach { walk(it, form, overlay) }
    }
    // The first owner is the content; the others are popups (an opened select box) drawn over it.
    scene.semanticsOwners.forEachIndexed { index, owner -> walk(owner.unmergedRootSemanticsNode, insideForm = false, overlay = index > 0) }
    return seen
}

/**
 * The parts the gate checks: the dialog's tagged parts (selects, the form's fields, the path and the
 * notices) and every plain text (a label, a note, an error). Popup parts only need to fit the window.
 */
private fun layoutNodesOf(scene: ImageComposeScene): List<PreviewChecks.LayoutNode> =
    nodesOf(scene).mapNotNull { n ->
        val isPart = n.tag?.startsWith("katachi.") == true
        if (!isPart && n.text == null) return@mapNotNull null
        val name = n.tag ?: "text:${n.text}"
        PreviewChecks.LayoutNode(
            name = name,
            left = n.left.toInt(),
            top = n.top.toInt(),
            right = n.right.toInt(),
            bottom = n.bottom.toInt(),
            scrolled = n.insideForm,
            textCut = n.cut,
            overlay = n.overlay,
            cutDetail = n.cutDetail,
        )
    }
