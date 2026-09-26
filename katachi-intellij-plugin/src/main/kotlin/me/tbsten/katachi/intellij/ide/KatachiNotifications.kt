package me.tbsten.katachi.intellij.ide

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import me.tbsten.katachi.intellij.model.GenerationReport

/**
 * Balloons of the `katachi` notification group, shown only while the tool window is hidden (spec 04
 * "通知"): whoever watches the tool window already sees the result.
 */
internal object KatachiNotifications {
    /** The `id` of `<notificationGroup>` in plugin.xml. */
    const val GROUP_ID: String = "katachi"

    fun generationFinished(project: Project, report: GenerationReport) {
        val files = report.writtenFiles.size
        val text = if (report.isComplete) {
            KatachiBundle.message("notification.generated", files)
        } else {
            KatachiBundle.message("notification.generationStopped", report.succeededCount, report.items.size)
        }
        notifyIfHidden(project, text, if (report.isComplete) NotificationType.INFORMATION else NotificationType.WARNING)
    }

    fun loadFailed(project: Project) {
        notifyIfHidden(project, KatachiBundle.message("notification.loadFailed"), NotificationType.ERROR)
    }

    /** Whether a notification would be shown now: the tool window is not on screen. */
    fun isToolWindowHidden(project: Project): Boolean =
        ToolWindowManager.getInstance(project).getToolWindow(KatachiToolWindowFactory.TOOL_WINDOW_ID)?.isVisible != true

    private fun notifyIfHidden(project: Project, text: String, type: NotificationType) {
        if (project.isDisposed || !isToolWindowHidden(project)) return
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
            .createNotification(text, type)
            .addAction(
                NotificationAction.createSimpleExpiring(KatachiBundle.message("notification.open")) {
                    ToolWindowManager.getInstance(project).getToolWindow(KatachiToolWindowFactory.TOOL_WINDOW_ID)?.activate(null)
                },
            )
            .notify(project)
    }
}
