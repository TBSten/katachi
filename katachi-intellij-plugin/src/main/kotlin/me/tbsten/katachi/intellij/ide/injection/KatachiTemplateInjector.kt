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

/**
 * Highlights the content of a katachi template as the language of the file it writes: the string a
 * `.ktFile().template { }` block returns as Kotlin, that of `.ktsFile().template { }` as a Kotlin
 * script. Which strings those are is [templateFileKind]'s business.
 *
 * `DumbAware` on purpose, like the plugin's other entries: nothing here reads an index or resolves a
 * symbol, only the PSI of the string and of the calls around it, so the highlighting is there while
 * the IDE is still indexing. Only the platform's own `MultiHostInjector` API is used (no IntelliLang,
 * no Kotlin injection internals), all of it present in Android Studio's 261 platform as well.
 *
 * The fragment is named with the extension of the file the template writes (`startInjecting`'s
 * `extension`). The K1 Kotlin plugin parses a `.kts` fragment as a script and a `.kt` one as a file;
 * the K2 plugin parses every injected Kotlin fragment as an unanalysed block code fragment instead
 * (`IDEKotlinK2KotlinParserDefinition`), in which top-level calls, declarations, `package` and
 * `import` all parse, so both kinds read without false errors either way. Nothing is resolved in a
 * fragment, so a template's imports of the app's own classes are not reported.
 *
 * An interpolation (`$name`, `${capture("x")}`) is not part of the Kotlin that is highlighted: the
 * literal pieces around it are joined into one fragment with a stand-in identifier where the value
 * goes, so `class ${name}Service` reads as `class nameService`. As what the stand-in makes of the
 * code is a guess (`${annotation}fun` becomes `katachiValuefun`), a fragment with one is marked
 * frankenstein, which keeps its syntax errors from being shown as the user's errors. A fragment
 * without one keeps them: there, what does not parse is what the template will write.
 *
 * ## Example 1: what is highlighted
 * ```kt
 * "*UseCase".ktFile().template {
 *     val name by stringParameter()
 *     """
 *         interface ${name}UseCase   // highlighted as Kotlin, `${name}` left as it is
 *     """.trimIndent()
 * }
 * ```
 */
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

/** One literal piece of the host: its range in the host, and the stand-ins for the values around it. */
internal data class InjectionPlace(val prefix: String, val range: TextRange, val suffix: String)

/**
 * The literal pieces of [host] (escapes included, the escaper decodes them), with every
 * interpolation between two pieces turned into a stand-in identifier: in front of the piece after
 * it, or after the last piece when the string ends with one. Empty when the string has no literal
 * text at all (`""`, `"$name"`).
 */
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

/** `$name` and `${name}` stand in as `name`; any other expression as [GENERIC_STAND_IN]. */
private fun standInFor(entry: KtStringTemplateEntryWithExpression): String {
    val expression = entry.expression
    return when {
        entry is KtSimpleNameStringTemplateEntry && expression is KtNameReferenceExpression -> expression.getReferencedName()
        entry is KtBlockStringTemplateEntry && expression is KtNameReferenceExpression -> expression.getReferencedName()
        else -> GENERIC_STAND_IN
    }
}

/** An identifier, so that it joins the text around it the way the value will: `${capture("name")}Service`. */
private const val GENERIC_STAND_IN = "katachiValue"
