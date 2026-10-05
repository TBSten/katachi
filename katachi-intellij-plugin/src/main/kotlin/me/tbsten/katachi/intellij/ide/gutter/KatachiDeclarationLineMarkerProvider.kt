package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import me.tbsten.katachi.intellij.ide.KatachiBundle
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

    override fun getId(): String = ID

    override fun getIcon(): Icon = KatachiIcons.gutter

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        GutterMarkerDrawing.collect(elements, result, portsOf)
    }

    companion object {
        /** What `Settings | Editor | General | Gutter Icons` keys the switch by: the Kotlin provider's and the other languages' share it. */
        const val ID: String = "me.tbsten.katachi.intellij.gutter"
    }
}
