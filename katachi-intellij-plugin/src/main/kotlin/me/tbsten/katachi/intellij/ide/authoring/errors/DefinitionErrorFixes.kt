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

/**
 * "Reload Definition": reads the definition through Gradle again, as `katachi.RefreshDefinition` does (design 2.4). The name is
 * ide-review-screens' name for the operation, English in both languages like the other quick fixes.
 *
 * The service is the one the annotation was worked out from, so the fix does not look it up again. It writes nothing:
 * `startInWriteAction` is false and there is no preview.
 *
 * ```kotlin
 * builder.withFix(ReloadDefinitionFix(definitions))
 * ```
 */
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

/**
 * "Show the whole message": the whole message of a mistake in a dialog, for when the hover is cut (design 2.4). [show] is what
 * puts the dialog on screen; a test replaces it.
 *
 * ```kotlin
 * builder.withFix(ShowWholeMessageFix(message))
 * ```
 */
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

/** The dialog of the whole message of a mistake. */
internal fun showErrorDialog(project: Project, message: String) {
    Messages.showErrorDialog(project, message, KatachiBundle.message("authoring.error.dialog.title"))
}
