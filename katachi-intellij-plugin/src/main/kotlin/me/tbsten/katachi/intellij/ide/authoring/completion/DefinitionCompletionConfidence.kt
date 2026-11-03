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

// Must run before `kotlinSkipAutopopupInStrings`, which stops automatic popups in any string.
internal class DefinitionCompletionConfidence : CompletionConfidence(), DumbAware {
    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        val atSite = sdkCall("decide whether to open the katachi popup") {
            PsiTreeUtil.getParentOfType(contextElement, KtStringTemplateExpression::class.java, false)?.completionSite() != null
        }.getOrNull() == true
        return if (atSite) ThreeState.NO else ThreeState.UNSURE
    }
}
