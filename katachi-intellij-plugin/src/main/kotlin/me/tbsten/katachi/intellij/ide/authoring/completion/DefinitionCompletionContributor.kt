package me.tbsten.katachi.intellij.ide.authoring.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import me.tbsten.katachi.intellij.ide.authoring.AuthoringPorts
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.presentation.authoring.CompletionCandidate
import me.tbsten.katachi.intellij.presentation.authoring.argCandidates
import me.tbsten.katachi.intellij.presentation.authoring.bindTemplate
import me.tbsten.katachi.intellij.presentation.authoring.captureCandidates
import me.tbsten.katachi.intellij.presentation.authoring.templateSpecCandidates
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Offers the names of the definition inside the strings that ask for one (ide-authoring design 3.1): the capture
 * names in `captureValue("…")`, the templates in a `templateSet`'s `template("…")`, the names a member takes in
 * `arg("…")`. The candidates are the latest answer of the definition JSON; with none, nothing is offered and Gradle
 * is not started (`ensureLoaded` is the existing, once-only way of asking for a first read).
 *
 * `DumbAware`, as the candidates come from the JSON and the place from the PSI. It never calls `stopHere()`: Kotlin's
 * and the platform's own candidates stay in the list. Ours carry a priority ([PRIORITY]), which is what puts them above
 * the neighbouring file names the platform offers inside a path-like string; the order of contributors does not.
 *
 * A choice replaces the whole text between the quotes, not only the part before the caret ([beforeCompletion]), so
 * `captureValue("na|me2")` becomes `captureValue("name")` on Tab.
 *
 * ```xml
 * <completion.contributor language="kotlin" order="first" implementationClass="...DefinitionCompletionContributor"/>
 * ```
 */
internal class DefinitionCompletionContributor : CompletionContributor(), DumbAware {

    override fun beforeCompletion(context: CompletionInitializationContext) {
        val file = context.file as? KtFile ?: return
        val caret = context.caret.offset
        val string = sdkCall("find the string around the caret") { stringAround(file.findElementAt(caret)) }.getOrNull() ?: return
        // Between the quotes only, and only a closed string: before the opening quote, after the closing one, or with no closing quote at all,
        // there is no "text up to the closing quote" to replace.
        if (!string.isClosed() || caret !in string.contentRange()) return
        if (sdkCall("look for a katachi completion site") { string.completionSite() }.getOrNull() == null) return
        // The closing quote: what is behind the caret up to it is replaced too.
        context.offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, string.textRange.endOffset - 1)
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        sdkCall("complete a name of the katachi definition") {
            val string = stringAround(parameters.position) ?: return@sdkCall
            val site = string.completionSite() ?: return@sdkCall
            val snapshots = answerOf(string.project) ?: return@sdkCall
            val candidates = completionCandidatesFor(site, parameters.originalFile, snapshots)
            if (candidates.isEmpty()) return@sdkCall
            val start = string.textRange.startOffset + 1
            if (parameters.offset < start) return@sdkCall
            val prefix = parameters.editor.document.charsSequence.subSequence(start, parameters.offset).toString()
            val matching = result.withPrefixMatcher(prefix)
            candidates.forEach { matching.addElement(PrioritizedLookupElement.withPriority(lookupOf(it), PRIORITY)) }
        }
    }

    private fun lookupOf(candidate: CompletionCandidate) = LookupElementBuilder.create(candidate.text)
        .withTypeText(candidate.typeText, true)
        .let { builder -> candidate.tailText?.let { builder.withTailText(it, true) } ?: builder }
        .withLookupStrings(candidate.aliases)

    private fun answerOf(project: Project): List<DefinitionSnapshot>? {
        val service = project.service<AuthoringPorts>().definition() ?: return null
        service.ensureLoaded()
        return service.state.value.answer?.definitions
    }

    /** The offsets a caret can have between the quotes of a closed string. */
    private fun KtStringTemplateExpression.contentRange(): IntRange = (textRange.startOffset + 1)..(textRange.endOffset - 1)

    private fun KtStringTemplateExpression.isClosed(): Boolean =
        lastChild?.node?.elementType == KtTokens.CLOSING_QUOTE

    private fun stringAround(element: PsiElement?): KtStringTemplateExpression? =
        PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java, false)

    private companion object {
        /** Above the file names and the like the platform offers in the same string. */
        const val PRIORITY = 100.0
    }
}

/**
 * The names a string of kind [site] in [file] offers, from the definition's [snapshots]: what the contributor lists, and what the
 * debug bridge reports. Call under a read lock.
 */
internal fun completionCandidatesFor(site: CompletionSite, file: PsiFile, snapshots: List<DefinitionSnapshot>): List<CompletionCandidate> =
    when (site) {
        is CompletionSite.CaptureValue -> {
            val service = file.project.service<AuthoringPorts>().definition()
            val binding = bindTemplate(file.name, site.templateLine, snapshots) { snapshot, ref ->
                val resolved = service?.resolve(snapshot.module.id, ref)
                resolved is ResolvedSite.Found && resolved.file == file.virtualFile
            }
            binding?.let(::captureCandidates).orEmpty()
        }
        CompletionSite.TemplateSpec -> templateSpecCandidates(snapshots)
        is CompletionSite.ArgName -> argCandidates(site.spec, snapshots)
    }
