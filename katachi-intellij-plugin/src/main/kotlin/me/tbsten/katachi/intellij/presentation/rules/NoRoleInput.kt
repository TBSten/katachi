package me.tbsten.katachi.intellij.presentation.rules

import me.tbsten.katachi.intellij.data.baseline.BaselineReading
import me.tbsten.katachi.intellij.model.ArchitectureModel
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import java.nio.file.Path

/** What the version control says of one file (design 2.2): only [Untracked] changes the wording of the notification. */
internal enum class VcsTracking {
    Tracked,

    /** The VCS knows the file and does not track it yet, so `gitTracked()` does not see it. */
    Untracked,
    Ignored,

    /** No VCS root holds the file, or the VCS could not be asked. */
    NoVcs,
}

/** The settings the no-role notification reads. Pure, so the decision does not depend on the IDE's settings service. */
internal data class RuleSettings(
    /** "Notify on files no role allows" (the new switch). */
    val notifyOnNoRole: Boolean = true,
    /** "Show editor notifications" (all of them). */
    val notificationsEnabled: Boolean = true,
    /** "Load the templates without the user asking": it also lets the definition load without a user action. */
    val loadWithoutUser: Boolean = true,
)

/**
 * Where a declaration is, as the notification shows it, without the `VirtualFile` that the IDE's
 * `ResolvedSite` holds: the popup's row prints `fileName:line`, or why the site is not known.
 */
internal sealed interface ResolvedSiteView {
    data class Found(val fileName: String, val line: Int) : ResolvedSiteView

    data class Unresolved(val reason: String) : ResolvedSiteView
}

/**
 * Everything the "no role holds this file" notification of one file depends on, gathered in the
 * background (design 2.1). [nearbySites] answers [FileRoles.nearby] position by position; a missing one
 * reads as unresolved.
 *
 * ```kotlin
 * val decision = decideNoRoleNotification(input.copy(vcs = VcsTracking.Untracked))
 * ```
 */
internal data class NoRoleInput(
    val file: Path,
    /** `forFile(file)`; `null` when no definition holds the file or nothing is loaded. */
    val roles: FileRoles?,
    val definition: DefinitionState,
    /** The `architecture { }` of the definition [roles] came from. */
    val architecture: ArchitectureModel?,
    val vcs: VcsTracking,
    val settings: RuleSettings,
    val memory: EditorNotificationMemory,
    val nearbySites: List<ResolvedSiteView>,
    /** Where `architecture { }` is written; `null` when it was not captured or not found. */
    val architectureSite: ResolvedSiteView?,
    /** What the project's baseline file says; [BaselineReading.NotConfigured] when `architecture { }` names none. */
    val baseline: BaselineReading = BaselineReading.NotConfigured,
)
