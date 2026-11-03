package me.tbsten.katachi.intellij.ide.rules

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.rules.roleSuggestionDraftFor

internal class CopyRoleSuggestionAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null && file != null && !file.isDirectory &&
            sdkCall("look for katachi") { DefinitionPorts.of(project).available() }.getOrNull() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        // forFile and the project root lookup are for the background (design 4).
        ApplicationManager.getApplication().executeOnPooledThread {
            val draft = sdkCall("write the role declaration draft of ${file.path}") { draftOf(project, file) }.getOrNull() ?: return@executeOnPooledThread
            ApplicationManager.getApplication().invokeLater { RuleEffectsImpl(project).copy(draft) }
        }
    }

    companion object {
        const val ID: String = "katachi.CopyRoleSuggestion"

        internal fun draftOf(project: Project, file: VirtualFile): String? {
            val roles = DefinitionPorts.of(project).service()?.forFile(file)
            if (roles != null) return roleSuggestionDraftFor(roles.relativePath)
            val base = project.guessProjectDir() ?: return null
            val relative = VfsUtilCore.getRelativePath(file, base, '/') ?: return null
            return roleSuggestionDraftFor(relative)
        }
    }
}
