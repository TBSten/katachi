package me.tbsten.katachi.intellij.ide.authoring.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.gutter.GutterMarkerDrawing
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.ide.gutter.KatachiIcons
import me.tbsten.katachi.intellij.ide.sdkCall
import org.jetbrains.kotlin.psi.KtFile
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.Icon

internal class KatachiDefinitionLineMarkerProvider(private val portsOf: (Project) -> GutterPorts) : LineMarkerProviderDescriptor(), DumbAware {
    constructor() : this(GutterPorts::of)

    override fun getName(): String = KatachiBundle.message("authoring.gutter.name")

    override fun getId(): String = ID

    override fun getIcon(): Icon = KatachiIcons.gutter

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val file = elements.firstOrNull()?.containingFile as? KtFile ?: return
        val ports = portsOf(file.project)
        val placed = sdkCall("compute the definition gutter of ${file.name}") { DefinitionGutter.of(file, ports) }.getOrNull().orEmpty()
        // The daemon asks once for the elements in the visible range and once for the rest: draw each ◆ in the call that has its leaf.
        val asked = elements.toCollection(Collections.newSetFromMap(IdentityHashMap()))
        for ((leaf, mark) in placed.filter { it.leaf in asked }) {
            result += LineMarkerInfo(
                leaf,
                leaf.textRange,
                KatachiIcons.gutter,
                { GutterMarkerDrawing.htmlOf(mark.tooltip) },
                DefinitionGutterMenu(file.project, ports, mark, file.viewProvider.virtualFile),
                GutterIconRenderer.Alignment.LEFT,
            ) { mark.tooltip }
        }
    }

    companion object {
        const val ID: String = "me.tbsten.katachi.intellij.authoring.gutter"
    }
}
