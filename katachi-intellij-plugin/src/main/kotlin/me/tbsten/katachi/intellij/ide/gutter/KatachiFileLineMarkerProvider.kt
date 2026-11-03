package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import me.tbsten.katachi.intellij.ide.KatachiBundle
import org.jetbrains.kotlin.psi.KtFile
import javax.swing.Icon

internal class KatachiFileLineMarkerProvider(private val portsOf: (Project) -> GutterPorts) : LineMarkerProviderDescriptor(), DumbAware {
    constructor() : this(GutterPorts::of)

    override fun getName(): String = KatachiBundle.message("gutter.marker.name")

    override fun getId(): String = KatachiDeclarationLineMarkerProvider.ID

    override fun getIcon(): Icon = KatachiIcons.gutter

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        // The Kotlin provider has `.kt` and `.kts`: a second ◆ here would stack on its.
        GutterMarkerDrawing.collect(elements, result, portsOf) { file -> file !is KtFile }
    }
}
