package me.tbsten.katachi.intellij.ide.rules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking

/**
 * What the version control says of [file] (design 4.4, U-6): no VCS holds it, it is ignored, it is not tracked
 * yet, or it is tracked. Reads memory only (`ChangeListManager`), so it is safe in the background under a
 * read lock and in dumb mode; any failure of the SDK reads as [VcsTracking.NoVcs], which words nothing about git.
 *
 * A file the change list manager has not scanned yet (one created a moment ago: it is "dirty") is not unversioned *yet*,
 * so the manager would call it tracked; this answers [VcsTracking.Untracked] for it, the wording a new file needs, and the
 * notification is drawn again once the manager has scanned it (see [settledVcsTrackingOf]).
 *
 * ```kotlin
 * val untracked = vcsTrackingOf(project, file) == VcsTracking.Untracked
 * ```
 */
internal fun vcsTrackingOf(project: Project, file: VirtualFile): VcsTracking =
    sdkCall("ask the VCS about ${file.path}") { readVcs(project, file).tracking }.getOrDefault(VcsTracking.NoVcs)

/**
 * What the version control says of [file] once the change list manager has scanned it, or `null` while it has not
 * ([vcsTrackingOf] guesses "untracked" then). The follow-up of a drawn notification compares with this, so a tracked file that is
 * being edited (dirty for a moment) does not flip its wording back and forth.
 */
internal fun settledVcsTrackingOf(project: Project, file: VirtualFile): VcsTracking? =
    sdkCall("ask the settled VCS status of ${file.path}") { readVcs(project, file).takeIf { it.settled }?.tracking }.getOrNull()

private fun readVcs(project: Project, file: VirtualFile): VcsReading {
    val hasVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file) != null
    if (!hasVcs) return VcsReading(hasVcs = false, ignored = false, unversioned = false, scanPending = false)
    val changes = ChangeListManager.getInstance(project)
    val pending = VcsDirtyScopeManager.getInstance(project).whatFilesDirty(listOf(VcsUtil.getFilePath(file))).isNotEmpty()
    return VcsReading(hasVcs = true, ignored = changes.isIgnoredFile(file), unversioned = changes.isUnversioned(file), scanPending = pending)
}

/** The answers of the platform for one file. */
internal data class VcsReading(val hasVcs: Boolean, val ignored: Boolean, val unversioned: Boolean, val scanPending: Boolean) {
    /** The three answers as one: no VCS wins, then ignored (an ignored file is also unversioned to some VCSs), then untracked. */
    val tracking: VcsTracking
        get() = when {
            !hasVcs -> VcsTracking.NoVcs
            ignored -> VcsTracking.Ignored
            unversioned -> VcsTracking.Untracked
            // The manager has not scanned the file: it cannot tell a new file from a tracked one, and "tracked" is the answer it gives by default.
            scanPending -> VcsTracking.Untracked
            else -> VcsTracking.Tracked
        }

    /** `false` when only the pending scan stands between "tracked" and "untracked": the answer may change when the scan ends. */
    val settled: Boolean get() = !(hasVcs && !ignored && !unversioned && scanPending)
}
