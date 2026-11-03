package me.tbsten.katachi.intellij.ide.gutter.annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.gutter.GutterActions
import me.tbsten.katachi.intellij.ide.gutter.GutterMenu
import me.tbsten.katachi.intellij.ide.gutter.GutterNavigation
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.ide.sdkCall

internal class GoToRuleFix(private val ports: GutterPorts, private val rule: RuleTarget) : IntentionAction, DumbAware {
    override fun getText(): String = KatachiBundle.message("gutter.annotation.goToRule", rule.label)

    override fun getFamilyName(): String = KatachiBundle.message("gutter.annotation.family")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        sdkCall("open the rule ${rule.label}") {
            val navigation = GutterNavigation(project, ports.notify)
            when (rule) {
                is RuleTarget.InFile -> navigation.openLine(rule.file, rule.line)
                is RuleTarget.AtPath -> navigation.openFile(rule.path, rule.line)
            }
        }
    }

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}

internal class RunCheckAgainFix(private val ports: GutterPorts, private val source: VirtualFile) : IntentionAction, DumbAware {
    override fun getText(): String = KatachiBundle.message("gutter.annotation.runCheck")

    override fun getFamilyName(): String = KatachiBundle.message("gutter.annotation.family")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = ports.action(GutterMenu.RUN_CHECK) != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        sdkCall("run the katachi check again") {
            val parent = editor?.let { DataManager.getInstance().getDataContext(it.contentComponent) } ?: DataContext.EMPTY_CONTEXT
            GutterActions.perform(project, ports, GutterMenu.RUN_CHECK, GutterActions.contextOf(project, source, parent))
        }.onFailure { ports.notify(KatachiBundle.message("gutter.annotation.failed", it.message.orEmpty())) }
    }

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}
