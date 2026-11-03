package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import me.tbsten.katachi.intellij.ide.KatachiBundle
import javax.swing.Icon

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
        // Shared with KatachiFileLineMarkerProvider: one Gutter Icons switch for both.
        const val ID: String = "me.tbsten.katachi.intellij.gutter"
    }
}
