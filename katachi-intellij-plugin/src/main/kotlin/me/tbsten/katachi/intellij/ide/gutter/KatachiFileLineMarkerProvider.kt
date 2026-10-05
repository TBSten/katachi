package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import me.tbsten.katachi.intellij.ide.KatachiBundle
import org.jetbrains.kotlin.psi.KtFile
import javax.swing.Icon

/**
 * The ◆ of a file that is not Kotlin (`.md`, `.xml`, `.toml`, plain text, ...) whose path a role holds (design 2.1): one,
 * on the first thing in the file that is not white space. Registered for every language (`language=""`), so it is asked
 * about Kotlin files too, and leaves those to [KatachiDeclarationLineMarkerProvider], which draws `.kt` and `.kts`.
 *
 * It shares the Kotlin provider's id, so `Settings | Editor | General | Gutter Icons` has one switch for both.
 *
 * ```kotlin
 * // plugin.xml
 * // <codeInsight.lineMarkerProvider language="" implementationClass="...KatachiFileLineMarkerProvider"/>
 * ```
 */
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
