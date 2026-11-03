package me.tbsten.katachi.intellij.ide.authoring.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.sdkCall
import org.jetbrains.kotlin.psi.KtFile

internal class DefinitionTypedHandler : TypedHandlerDelegate(), DumbAware {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (charTyped != '"' || file !is KtFile) return Result.CONTINUE
        val offset = editor.caretModel.offset
        val before = editor.document.charsSequence.subSequence((offset - LOOK_BEHIND).coerceAtLeast(0), offset)
        if (!OPENS_NAME.containsMatchIn(before)) return Result.CONTINUE
        sdkCall("schedule the katachi completion popup") { AutoPopupController.getInstance(project).scheduleAutoPopup(editor) }
        return Result.CONTINUE
    }

    private companion object {
        const val LOOK_BEHIND = 48

        val OPENS_NAME = Regex("""\b(captureValue|template|arg)\(\s*(\w+\s*=\s*)?$""")
    }
}
