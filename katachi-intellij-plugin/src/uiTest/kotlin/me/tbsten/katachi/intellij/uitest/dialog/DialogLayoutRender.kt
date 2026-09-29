@file:OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)

package me.tbsten.katachi.intellij.uitest.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.LocalStaticRendering
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogActions
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogContent
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogStrings
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogUiState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.skia.EncodedImageFormat

/** A window the dialog is drawn in; the real one is resizable, so any size within a minimum must hold. */
internal data class WindowSize(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x$height"
}

/** One part of the dialog as the semantics tree reports it (window coordinates, in px at density 1). */
internal data class PartBox(val tag: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: PartBox, tolerance: Float = 1f): Boolean =
        left < other.right - tolerance && other.left < right - tolerance && top < other.bottom - tolerance && other.top < bottom - tolerance

    override fun toString(): String = "$tag[$left,$top - $right,$bottom]"
}

/** What one drawing produced: the PNG and every tagged part's box. */
internal class DialogFrame(val png: ByteArray, val parts: List<PartBox>)

private const val FRAME_NANOS = 16_666_667L
private val noActions = object : GenerateDialogActions {
    override fun onSelectTemplate(index: Int) = Unit

    override fun onSelectDefinition(index: Int) = Unit

    override fun onInput(name: String, value: String) = Unit

    override fun onGenerate() = Unit
}

/** Draws [ui] as the dialog does at [size] on standalone Jewel and reads where its tagged parts ended up. */
internal fun drawDialog(ui: GenerateDialogUiState, strings: GenerateDialogStrings, size: WindowSize, dark: Boolean = false): DialogFrame {
    val scene = ImageComposeScene(size.width, size.height, Density(1f)) {
        IntUiTheme(isDark = dark) {
            CompositionLocalProvider(LocalStaticRendering provides true) {
                Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
                    GenerateDialogContent(ui, strings, noActions)
                }
            }
        }
    }
    try {
        var now = 0L
        repeat(3) { scene.render(now.also { now += FRAME_NANOS }) }
        val image = scene.render(now)
        return DialogFrame(image.encodeToData(EncodedImageFormat.PNG)!!.bytes, partsOf(scene))
    } finally {
        scene.close()
    }
}

private fun partsOf(scene: ImageComposeScene): List<PartBox> {
    val seen = mutableListOf<PartBox>()

    fun walk(node: SemanticsNode) {
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        if (tag != null && tag.startsWith("katachi.")) {
            val at = node.positionInRoot
            seen += PartBox(tag, at.x, at.y, at.x + node.size.width, at.y + node.size.height)
        }
        node.children.forEach(::walk)
    }
    scene.semanticsOwners.forEach { walk(it.unmergedRootSemanticsNode) }
    return seen
}

private const val EPSILON = 1f

/**
 * What must hold of a drawing at [size]: no part leaves the window sideways; the selects, the form
 * and the path stay in the window and do not overlap each other; every input of the state is drawn (as [expectedFields]); the form's fields (which scroll, so
 * only their sideways extent and their order matter) stay inside the form's width and never overlap.
 */
internal fun layoutProblemsOf(frame: DialogFrame, size: WindowSize, expectedFields: Int): List<String> {
    val problems = mutableListOf<String>()
    val parts = frame.parts
    val fields = parts.filter { it.tag.startsWith("katachi.field:") }
    if (fields.size != expectedFields) problems += "$expectedFields inputs expected but the drawing has ${fields.size}: ${fields.map { it.tag }}"
    val form = parts.firstOrNull { it.tag == KatachiTestTags.DIALOG_FORM }
    val fixed = parts.filter { it.tag.startsWith("katachi.dialog.") && it.tag != KatachiTestTags.DIALOG_FORM }

    (fixed + listOfNotNull(form)).forEach { part ->
        if (part.left < -EPSILON || part.right > size.width + EPSILON || part.top < -EPSILON || part.bottom > size.height + EPSILON) {
            problems += "$part leaves the ${size.width}x${size.height} window"
        }
    }
    fields.forEach { field ->
        if (field.left < -EPSILON || field.right > size.width + EPSILON) problems += "input $field leaves the window sideways (width ${size.width})"
        if (form != null && (field.left < form.left - EPSILON || field.right > form.right + EPSILON)) problems += "input $field is wider than the form $form"
    }
    fields.forEachIndexed { i, a ->
        fields.drop(i + 1).forEach { b -> if (a.overlaps(b)) problems += "inputs overlap: $a and $b" }
    }
    val stacked = (fixed + listOfNotNull(form)).sortedBy { it.top }
    stacked.forEachIndexed { i, a ->
        stacked.drop(i + 1).forEach { b -> if (a.overlaps(b)) problems += "parts overlap: $a and $b" }
    }
    return problems
}
