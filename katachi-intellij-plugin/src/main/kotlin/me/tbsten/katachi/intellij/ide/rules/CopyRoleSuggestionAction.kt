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

/**
 * `katachi.CopyRoleSuggestion`: puts a `.role` declaration for the selected file on the clipboard, the draft the test's
 * report suggests under an UnexpectedFile (the popup of the no-role notification has the same row). Works on any file
 * of a project with katachi, also one a role already holds, as a starting point for a new role; Find Action reaches it.
 *
 * ```kotlin
 * ActionManager.getInstance().getAction(CopyRoleSuggestionAction.ID)
 * ```
 */
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
        /** The `id` of the action in plugin.xml. */
        const val ID: String = "katachi.CopyRoleSuggestion"

        /**
         * The draft for [file]: relative to the definition's project root when a definition holds it, else relative to the
         * project. `null` for a file outside both.
         */
        internal fun draftOf(project: Project, file: VirtualFile): String? {
            val roles = DefinitionPorts.of(project).service()?.forFile(file)
            if (roles != null) return roleSuggestionDraftFor(roles.relativePath)
            val base = project.guessProjectDir() ?: return null
            val relative = VfsUtilCore.getRelativePath(file, base, '/') ?: return null
            return roleSuggestionDraftFor(relative)
        }
    }
}
