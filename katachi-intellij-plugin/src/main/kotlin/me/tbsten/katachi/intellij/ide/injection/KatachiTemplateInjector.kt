package me.tbsten.katachi.intellij.ide.injection

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import me.tbsten.katachi.intellij.ide.sdkCall
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

// PSI only (no index, no resolve) so it runs in dumb mode; K1 reads a `.kts` fragment as a script, K2 reads every fragment as a block code fragment.
// A fragment with an interpolation is marked frankenstein: its stand-in identifier is a guess, so its syntax errors must not be shown.
internal class KatachiTemplateInjector : MultiHostInjector, DumbAware {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(KtStringTemplateExpression::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val host = context as? KtStringTemplateExpression ?: return
        // A failure here must cost the highlighting of one string, never an IDE error while typing.
        sdkCall("inject Kotlin into a katachi template") {
            if (!host.isValidHost) return@sdkCall
            val kind = host.templateFileKind() ?: return@sdkCall
            val places = placesOf(host)
            if (places.isEmpty()) return@sdkCall
            registrar.startInjecting(KotlinLanguage.INSTANCE, kind.extension)
            if (places.any { it.prefix.isNotEmpty() || it.suffix.isNotEmpty() }) {
                registrar.frankensteinInjection(true)
            }
            places.forEach { place -> registrar.addPlace(place.prefix, place.suffix, host, place.range) }
            registrar.doneInjecting()
        }
    }
}

internal data class InjectionPlace(val prefix: String, val range: TextRange, val suffix: String)

internal fun placesOf(host: KtStringTemplateExpression): List<InjectionPlace> {
    val pieces = mutableListOf<TextRange>()
    // The stand-ins in front of each piece, and after the last one.
    val standIns = mutableListOf<String>()
    var pendingStandIn = ""
    for (entry in host.entries) {
        if (entry is KtStringTemplateEntryWithExpression) {
            pendingStandIn += standInFor(entry)
            continue
        }
        val range = entry.textRangeInParent
        val last = pieces.lastOrNull()
        if (last != null && pendingStandIn.isEmpty() && last.endOffset == range.startOffset) {
            pieces[pieces.lastIndex] = last.union(range)
        } else {
            pieces += range
            standIns += pendingStandIn
            pendingStandIn = ""
        }
    }
    return pieces.mapIndexed { index, range ->
        InjectionPlace(
            prefix = standIns[index],
            range = range,
            suffix = if (index == pieces.lastIndex) pendingStandIn else "",
        )
    }
}

private fun standInFor(entry: KtStringTemplateEntryWithExpression): String {
    val expression = entry.expression
    return when {
        entry is KtSimpleNameStringTemplateEntry && expression is KtNameReferenceExpression -> expression.getReferencedName()
        entry is KtBlockStringTemplateEntry && expression is KtNameReferenceExpression -> expression.getReferencedName()
        else -> GENERIC_STAND_IN
    }
}

private const val GENERIC_STAND_IN = "katachiValue"
