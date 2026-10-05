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

/**
 * What the two line marker providers (the Kotlin one and the one for every other language) do with the daemon's
 * elements: ask [GutterPlacement] where the ◆ go and turn each into a [LineMarkerInfo] with its tooltip and menu.
 *
 * Call from the provider's `collectSlowLineMarkers` (the daemon's background thread).
 *
 * ```kotlin
 * override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) =
 *     GutterMarkerDrawing.collect(elements, result, portsOf) { file -> file !is KtFile }
 * ```
 */
internal object GutterMarkerDrawing {
    /** [accepts] says whether the provider draws on the file at all; the ◆ of a file's other provider are not its business. */
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

    /** The gutter draws the tooltip as HTML, so a rule's `<T>` and `&` are escaped for it; the accessible name stays the raw text. */
    private fun markerOf(project: Project, ports: GutterPorts, leaf: PsiElement, mark: GutterMark, menu: CapturedMenu?): LineMarkerInfo<PsiElement> =
        LineMarkerInfo(
            leaf,
            rangeOf(leaf),
            KatachiIcons.gutter,
            { htmlOf(mark.tooltip) },
            menu?.let { GutterMenu(project, ports, it) },
            GutterIconRenderer.Alignment.LEFT,
        ) { mark.tooltip }

    /**
     * The range the gutter keys the ◆ on: [leaf]'s, without the white space it starts with. A plain text file is one leaf from
     * its first line, and the ◆ belongs on the first line that has something in it.
     */
    private fun rangeOf(leaf: PsiElement): TextRange {
        val range = leaf.textRange
        val skipped = leaf.text.length - leaf.text.trimStart().length
        return if (skipped in 1 until range.length) TextRange(range.startOffset + skipped, range.endOffset) else range
    }

    /** [text] as the HTML the gutter shows: its characters escaped, a new line a `<br>`. */
    fun htmlOf(text: String): String = StringUtil.escapeXmlEntities(text).replace("\n", "<br>")
}

/** The menus of one call's ◆, looked up in the background. The ◆ of a file share the role's sites, so each is resolved once. */
private class MenuCaptures(private val ports: GutterPorts, private val placed: PlacedFile, private val file: VirtualFile) {
    private val resolved = HashMap<DeclarationSiteRef, ResolvedSite>()
    private val running = sdkCall("read the running checks") { ports.violations()?.runningChecks }.getOrNull()

    /** `null` when the menu could not be put together: the ◆ is still drawn, it just opens nothing. */
    fun of(mark: GutterMark): CapturedMenu? =
        sdkCall("capture the menu of ${mark.tooltip}") { GutterMenuCapture.capture(placed, mark, file, running, resolved) }.getOrNull()
}
