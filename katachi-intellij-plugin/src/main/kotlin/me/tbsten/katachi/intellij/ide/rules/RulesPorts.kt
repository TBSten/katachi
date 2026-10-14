package me.tbsten.katachi.intellij.ide.rules

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.data.baseline.BaselineReading
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.notification.EditorNotificationMemoryService
import me.tbsten.katachi.intellij.presentation.rules.RuleSettings
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import java.nio.file.Path

/**
 * What the "no role holds this file" notification reads of one project, as `NotificationPorts` is for the template
 * notification. Tests swap the definition service (a fake), the settings and the VCS; the platform uses [of], which
 * is wired to the real services.
 *
 * Every function is called in the background under a read lock and may throw: the provider runs them inside `sdkCall`.
 *
 * ```kotlin
 * val ports = RulesPorts.of(project)
 * val service = ports.definitions() ?: return null   // null: a project without katachi
 * ```
 */
internal class RulesPorts(
    /** The definition service, or `null` in a project without katachi (no service is created there, decision 17). */
    val definitions: () -> DefinitionModelService?,
    val settings: () -> RuleSettings,
    val memory: () -> EditorNotificationMemoryService,
    /** What the VCS says of the file for a first draw; a file the VCS has not scanned yet reads as untracked. */
    val vcs: (VirtualFile) -> VcsTracking,
    /** What the VCS says once it has scanned the file, `null` before: what a drawn notification is compared with. */
    val vcsIfSettled: (VirtualFile) -> VcsTracking? = vcs,
    val effects: () -> RuleEffects,
    /** What the baseline file [baseline] names (relative to the definition's project root [projectRoot]) says. Reads the disk. */
    val baseline: (projectRoot: Path, baseline: String) -> BaselineReading = { _, _ -> BaselineReading.Missing },
) {
    companion object {
        fun of(project: Project): RulesPorts = RulesPorts(
            definitions = { DefinitionPorts.of(project).service() },
            settings = { ruleSettingsOf(KatachiSettings.getInstance(project)) },
            memory = { project.service<EditorNotificationMemoryService>() },
            vcs = { file -> vcsTrackingOf(project, file) },
            vcsIfSettled = { file -> settledVcsTrackingOf(project, file) },
            effects = { RuleEffectsImpl(project) },
            baseline = { root, baseline -> project.service<KatachiBaselineService>().reading(root.resolve(baseline)) },
        )
    }
}

/** The no-role notification's part of the katachi settings. */
internal fun ruleSettingsOf(settings: KatachiSettings): RuleSettings = RuleSettings(
    notifyOnNoRole = settings.notifyOnNoRole,
    notificationsEnabled = settings.editorNotificationEnabled,
    loadWithoutUser = settings.autoLoadTemplates,
)
