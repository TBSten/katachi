package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.data.detect.detectDefinitionModules
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.ProjectDataModuleSource
import me.tbsten.katachi.intellij.model.DetectionResult
import me.tbsten.katachi.intellij.presentation.entry.EntrySettings

/**
 * What [KatachiEditorNotificationProvider] reads of one project. Tests swap the synced data and the
 * services for their own; the platform uses [of].
 *
 * Every function is called in the background under a read lock and may throw: the provider runs
 * them inside `sdkCall`.
 *
 * ```kotlin
 * val ports = NotificationPorts.of(project)
 * val service = ports.existingService() ?: if (ports.hasDefinitionModule()) ports.service() else null
 * ```
 */
internal class NotificationPorts(
    /** The project service when something already created it, without creating it. */
    val existingService: () -> KatachiProjectService?,
    /** Whether the synced data has a katachi definition module; reads it, never runs Gradle (decision 17). */
    val hasDefinitionModule: () -> Boolean,
    /** The project service, created on the first call. Only asked for when [hasDefinitionModule] (decision 17). */
    val service: () -> KatachiProjectService,
    val settings: () -> EntrySettings,
    val memory: () -> EditorNotificationMemoryService,
) {
    companion object {
        fun of(project: Project): NotificationPorts = NotificationPorts(
            existingService = { project.getServiceIfCreated(KatachiProjectService::class.java) },
            hasDefinitionModule = { hasDefinitionModule(project) },
            service = { KatachiProjectService.getInstance(project) },
            settings = { entrySettingsOf(KatachiSettings.getInstance(project)) },
            memory = { project.service<EditorNotificationMemoryService>() },
        )

        private fun hasDefinitionModule(project: Project): Boolean =
            when (detectDefinitionModules(ProjectDataModuleSource(project).read())) {
                is DetectionResult.Found, is DetectionResult.TaskListMissing -> true
                else -> false
            }
    }
}

/** The notification's part of the katachi settings (B2's `KatachiSettings`). */
internal fun entrySettingsOf(settings: KatachiSettings): EntrySettings = EntrySettings(
    notificationsEnabled = settings.editorNotificationEnabled,
    emptyFileNotification = settings.notifyOnEmptyFile,
    contentFileNotification = settings.notifyOnFileWithContent,
    contentFirstTimeOnly = settings.notifyOnContentOnce,
    loadWithoutUser = settings.autoLoadTemplates,
)
