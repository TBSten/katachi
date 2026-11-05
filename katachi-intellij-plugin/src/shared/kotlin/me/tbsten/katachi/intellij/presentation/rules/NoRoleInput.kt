package me.tbsten.katachi.intellij.presentation.rules

import me.tbsten.katachi.intellij.data.baseline.BaselineReading
import me.tbsten.katachi.intellij.model.ArchitectureModel
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import java.nio.file.Path

internal enum class VcsTracking {
    Tracked,

    Untracked,
    Ignored,

    NoVcs,
}

internal data class RuleSettings(
    val notifyOnNoRole: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val loadWithoutUser: Boolean = true,
)

internal sealed interface ResolvedSiteView {
    data class Found(val fileName: String, val line: Int) : ResolvedSiteView

    data class Unresolved(val reason: String) : ResolvedSiteView
}

internal data class NoRoleInput(
    val file: Path,
    val roles: FileRoles?,
    val definition: DefinitionState,
    val architecture: ArchitectureModel?,
    val vcs: VcsTracking,
    val settings: RuleSettings,
    val memory: EditorNotificationMemory,
    val nearbySites: List<ResolvedSiteView>,
    val architectureSite: ResolvedSiteView?,
    val baseline: BaselineReading = BaselineReading.NotConfigured,
)
