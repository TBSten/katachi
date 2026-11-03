package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.PlacementUnavailableReason
import me.tbsten.katachi.intellij.ide.DefinitionModulePresence
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.presentation.entry.EntrySettings

// Every function is called in the background under a read lock and may throw: the provider runs them inside `sdkCall`.
internal class NotificationPorts(
    val existingService: () -> KatachiProjectService?,
    val hasDefinitionModule: () -> Boolean,
    val service: () -> KatachiProjectService,
    val settings: () -> EntrySettings,
    val memory: () -> EditorNotificationMemoryService,
) {
    fun serviceForEntries(): KatachiProjectService? = existingService() ?: if (hasDefinitionModule()) service() else null

    companion object {
        fun of(project: Project): NotificationPorts = NotificationPorts(
            existingService = { project.getServiceIfCreated(KatachiProjectService::class.java) },
            hasDefinitionModule = { DefinitionModulePresence.getInstance(project).hasDefinitionModule() },
            service = { KatachiProjectService.getInstance(project) },
            settings = { entrySettingsOf(KatachiSettings.getInstance(project)) },
            memory = { project.service<EditorNotificationMemoryService>() },
        )
    }
}

// Must ask again once loading without the user is turned back on, else New and the notification stay away until the tool window opens or Gradle syncs.
internal fun shouldEnsureLoaded(state: PlacementIndexState, loadWithoutUser: Boolean): Boolean =
    state is PlacementIndexState.NotLoaded ||
        (loadWithoutUser && state == PlacementIndexState.Unavailable(PlacementUnavailableReason.LoadingWithoutUserDisabled))

internal fun entrySettingsOf(settings: KatachiSettings): EntrySettings = EntrySettings(
    notificationsEnabled = settings.editorNotificationEnabled,
    emptyFileNotification = settings.notifyOnEmptyFile,
    contentFileNotification = settings.notifyOnFileWithContent,
    contentFirstTimeOnly = settings.notifyOnContentOnce,
    loadWithoutUser = settings.autoLoadTemplates,
)
