package me.tbsten.katachi.intellij.ide.authoring.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import me.tbsten.katachi.intellij.ide.sdkCall
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Lets the completion popup open by itself inside the strings of a definition that ask for a name (design 3.4).
 * Kotlin stops an automatic popup in any string; saying "do not skip" here, ahead of that, is the way the Kotlin
 * plugin itself lets its scripts' strings complete. Everywhere else it has no opinion.
 *
 * ```xml
 * <completion.confidence language="kotlin" order="before kotlinSkipAutopopupInStrings" implementationClass="...DefinitionCompletionConfidence"/>
 * ```
 */
internal class DefinitionCompletionConfidence : CompletionConfidence(), DumbAware {
    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        val atSite = sdkCall("decide whether to open the katachi popup") {
            PsiTreeUtil.getParentOfType(contextElement, KtStringTemplateExpression::class.java, false)?.completionSite() != null
        }.getOrNull() == true
        return if (atSite) ThreeState.NO else ThreeState.UNSURE
    }
}
