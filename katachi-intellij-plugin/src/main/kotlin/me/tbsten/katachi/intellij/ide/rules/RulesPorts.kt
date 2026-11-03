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

// Every function is called in the background under a read lock and may throw: the provider runs them inside `sdkCall`.
internal class RulesPorts(
    val definitions: () -> DefinitionModelService?,
    val settings: () -> RuleSettings,
    val memory: () -> EditorNotificationMemoryService,
    val vcs: (VirtualFile) -> VcsTracking,
    val vcsIfSettled: (VirtualFile) -> VcsTracking? = vcs,
    val effects: () -> RuleEffects,
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

internal fun ruleSettingsOf(settings: KatachiSettings): RuleSettings = RuleSettings(
    notifyOnNoRole = settings.notifyOnNoRole,
    notificationsEnabled = settings.editorNotificationEnabled,
    loadWithoutUser = settings.autoLoadTemplates,
)
