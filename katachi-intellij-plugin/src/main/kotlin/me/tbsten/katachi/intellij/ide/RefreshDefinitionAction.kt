package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.definition.RefreshReason

/**
 * `katachi.RefreshDefinition` (design 4.8): reads the architecture definition again through Gradle, as
 * the tool window's reload does. The two features call it from their tooltips and popups when the
 * definition is stale. Shown only in a project with katachi.
 *
 * ```kotlin
 * ActionManager.getInstance().getAction(RefreshDefinitionAction.ID)
 * ```
 */
internal class RefreshDefinitionAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && sdkCall("look for katachi") { DefinitionPorts.of(project).available() }.getOrNull() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        refresh(e.project ?: return)
    }

    companion object {
        /** What the action does, for the popups that offer it as a row of their own. */
        fun refresh(project: Project) {
            // Finding the service may read the synced data, which a large Android build makes too slow for the EDT.
            ApplicationManager.getApplication().executeOnPooledThread {
                sdkCall("reload the definition") { DefinitionPorts.of(project).service()?.refresh(RefreshReason.UserAction) }
            }
        }

        /** The `id` of the action in plugin.xml. */
        const val ID: String = "katachi.RefreshDefinition"
    }
}
