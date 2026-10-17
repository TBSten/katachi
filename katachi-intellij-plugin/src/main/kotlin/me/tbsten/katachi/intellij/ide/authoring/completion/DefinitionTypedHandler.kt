package me.tbsten.katachi.intellij.ide.authoring.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.sdkCall
import org.jetbrains.kotlin.psi.KtFile

/**
 * Opens the completion popup when the quote that opens the first argument of `captureValue(`, `template(` or `arg(`
 * is typed (design 3.4). The popup then shows only if [DefinitionCompletionConfidence] and the contributor agree that
 * the string is a place for a name, so a `template("` of another library costs a popup that finds nothing.
 *
 * ```kotlin
 * // captureValue(   -> type "   -> the popup opens with the capture names
 * ```
 */
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

        /** `captureValue(`, `template(`, `arg(name = ` in front of the caret: the quote being typed opens the first argument. */
        val OPENS_NAME = Regex("""\b(captureValue|template|arg)\(\s*(\w+\s*=\s*)?$""")
    }
}
