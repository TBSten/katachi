package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.presentation.gutter.GutterMark
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.Icon

/**
 * The ◆ of a Kotlin file (`.kt` and `.kts`) whose path a role holds (design 2.1): one per top-level declaration when a
 * role's `ktFileContent { }` restricts them, else one for the file. Listed in `Settings | Editor | General | Gutter
 * Icons` as "katachi roles of source files".
 *
 * Everything is computed in [collectSlowLineMarkers] (the daemon's background thread); the EDT only shows the
 * tooltip and the menu from what was captured. Works in dumb mode: it reads the PSI's top-level declarations,
 * never an index.
 *
 * ```kotlin
 * // plugin.xml
 * // <codeInsight.lineMarkerProvider language="kotlin" implementationClass="...KatachiDeclarationLineMarkerProvider"/>
 * ```
 */
internal class KatachiDeclarationLineMarkerProvider(private val portsOf: (Project) -> GutterPorts) : LineMarkerProviderDescriptor(), DumbAware {
    constructor() : this(GutterPorts::of)

    override fun getName(): String = KatachiBundle.message("gutter.marker.name")

    override fun getIcon(): Icon = KatachiIcons.gutter

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val file = elements.firstOrNull()?.containingFile ?: return
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
            leaf.textRange,
            KatachiIcons.gutter,
            { StringUtil.escapeXmlEntities(mark.tooltip) },
            menu?.let { GutterMenu(project, ports, it) },
            GutterIconRenderer.Alignment.LEFT,
        ) { mark.tooltip }
}

/** The menus of one call's ◆, looked up in the background. The ◆ of a file share the role's sites, so each is resolved once. */
private class MenuCaptures(private val ports: GutterPorts, private val placed: PlacedFile, private val file: VirtualFile) {
    private val resolved = HashMap<DeclarationSiteRef, ResolvedSite>()
    private val running = sdkCall("read the running checks") { ports.violations()?.runningChecks }.getOrNull()

    /** `null` when the menu could not be put together: the ◆ is still drawn, it just opens nothing. */
    fun of(mark: GutterMark): CapturedMenu? =
        sdkCall("capture the menu of ${mark.tooltip}") { GutterMenuCapture.capture(placed, mark, file, running, resolved) }.getOrNull()
}
