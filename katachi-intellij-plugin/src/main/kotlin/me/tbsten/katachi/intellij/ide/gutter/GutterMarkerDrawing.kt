package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.presentation.gutter.GutterMark
import java.util.Collections
import java.util.IdentityHashMap

// Call from `collectSlowLineMarkers` (the daemon's background thread).
internal object GutterMarkerDrawing {
    fun collect(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
        portsOf: (Project) -> GutterPorts,
        accepts: (PsiFile) -> Boolean = { true },
    ) {
        val file = elements.firstOrNull()?.containingFile ?: return
        if (!accepts(file)) return
        // A file with several PSI trees (Markdown has a Markdown one and an HTML one) is asked once per tree: the base language's draws.
        if (file.language != file.viewProvider.baseLanguage) return
        // The plugin's own injection puts Kotlin in a definition file's `.template { }`; the daemon asks for the fragment too, and the definition file gets no ◆ (design 2.1).
        if (InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)) return
        val ports = portsOf(file.project)
        val placed = sdkCall("compute the katachi gutter of ${file.name}") { GutterPlacement.ofFile(file, ports) }.getOrNull() ?: return
        // The daemon asks once for the elements in the visible range and once for the rest: draw each ◆ in the call that has its leaf.
        val asked = elements.toCollection(Collections.newSetFromMap(IdentityHashMap()))
        val drawn = placed.marks.filter { it.leaf in asked }
        if (drawn.isEmpty()) return
        val menus = MenuCaptures(ports, placed, file.viewProvider.virtualFile)
        for ((leaf, mark) in drawn) result += markerOf(file.project, ports, leaf, mark, menus.of(mark))
    }

    private fun markerOf(project: Project, ports: GutterPorts, leaf: PsiElement, mark: GutterMark, menu: CapturedMenu?): LineMarkerInfo<PsiElement> =
        LineMarkerInfo(
            leaf,
            rangeOf(leaf),
            KatachiIcons.gutter,
            { htmlOf(mark.tooltip) },
            menu?.let { GutterMenu(project, ports, it) },
            GutterIconRenderer.Alignment.LEFT,
        ) { mark.tooltip }

    private fun rangeOf(leaf: PsiElement): TextRange {
        val range = leaf.textRange
        val skipped = leaf.text.length - leaf.text.trimStart().length
        return if (skipped in 1 until range.length) TextRange(range.startOffset + skipped, range.endOffset) else range
    }

    fun htmlOf(text: String): String = StringUtil.escapeXmlEntities(text).replace("\n", "<br>")
}

private class MenuCaptures(private val ports: GutterPorts, private val placed: PlacedFile, private val file: VirtualFile) {
    private val resolved = HashMap<DeclarationSiteRef, ResolvedSite>()
    private val running = sdkCall("read the running checks") { ports.violations()?.runningChecks }.getOrNull()

    fun of(mark: GutterMark): CapturedMenu? =
        sdkCall("capture the menu of ${mark.tooltip}") { GutterMenuCapture.capture(placed, mark, file, running, resolved) }.getOrNull()
}
