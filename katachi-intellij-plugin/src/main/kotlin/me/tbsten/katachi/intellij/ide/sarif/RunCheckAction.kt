package me.tbsten.katachi.intellij.ide.sarif

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiNotifications
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.ModuleId

internal class RunCheckAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && sdkCall("look for katachi") { DefinitionPorts.of(project).available() }.getOrNull() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        // Finding the services, and the modules in the synced data, are too slow for the EDT (a large Android build).
        ApplicationManager.getApplication().executeOnPooledThread { sdkCall("start the katachi check") { start(project, file) } }
    }

    private fun start(project: Project, file: VirtualFile?) {
        val ports = DefinitionPorts.of(project)
        val definition = ports.service() ?: return
        val violations = ports.violations() ?: return
        val projectService = KatachiProjectService.getInstance(project)
        projectService.scope.launch {
            // Gradle compiles the definition from the disk.
            withContext(Dispatchers.EDT) {
                sdkCall("save the files before the check") { writeIntentReadAction { FileDocumentManager.getInstance().saveAllDocuments() } }
            }
            val module = moduleToCheck(project, definition, file)
            if (module == null) notifyNoTarget(project) else violations.runCheck(module)
        }
    }

    companion object {
        const val ID: String = "katachi.RunCheck"

        // Call it off the EDT.
        fun moduleToCheck(project: Project, definition: DefinitionModelService, file: VirtualFile?): ModuleId? {
            file?.let { definition.forFile(it) }?.let { return it.module.id }
            val modules = sdkCall("find the definition modules") { KatachiProjectService.getInstance(project).definitionModules() }.getOrNull().orEmpty()
            val path = file?.let { sdkCall("read the path of $it") { it.fileSystem.getNioPath(it) }.getOrNull() }
            path?.let { modules.filter { module -> it.startsWith(module.directory) }.maxByOrNull { module -> module.directory.nameCount } }?.let { return it.id }
            definition.definitions.singleOrNull()?.let { return it.module.id }
            return modules.singleOrNull()?.id
        }

        private fun notifyNoTarget(project: Project) {
            if (project.isDisposed) return
            sdkCall("tell that the check has no module") {
                NotificationGroupManager.getInstance().getNotificationGroup(KatachiNotifications.GROUP_ID)
                    .createNotification(KatachiBundle.message("violations.checkTargetUnknown"), NotificationType.WARNING)
                    .notify(project)
            }
        }
    }
}
