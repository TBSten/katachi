package me.tbsten.katachi.intellij.ide.authoring.errors

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.RefreshReason
import me.tbsten.katachi.intellij.ide.sdkCall

internal class ReloadDefinitionFix(private val definitions: DefinitionModelService) : IntentionAction, DumbAware {
    override fun getText(): String = KatachiBundle.message("authoring.error.fix.reload")

    override fun getFamilyName(): String = KatachiBundle.message("authoring.error.fix.family")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        sdkCall("reload the definition") { definitions.refresh(RefreshReason.UserAction) }
    }

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}

internal class ShowWholeMessageFix(
    private val message: String,
    private val show: (Project, String) -> Unit = ::showErrorDialog,
) : IntentionAction, DumbAware {
    override fun getText(): String = KatachiBundle.message("authoring.error.fix.showMessage")

    override fun getFamilyName(): String = KatachiBundle.message("authoring.error.fix.family")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        sdkCall("show the whole message of a definition error") { show(project, message) }
    }

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}

internal fun showErrorDialog(project: Project, message: String) {
    Messages.showErrorDialog(project, message, KatachiBundle.message("authoring.error.dialog.title"))
}
