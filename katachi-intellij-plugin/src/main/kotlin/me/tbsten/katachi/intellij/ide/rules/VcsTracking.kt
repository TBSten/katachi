package me.tbsten.katachi.intellij.ide.rules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking

// Reads memory only (`ChangeListManager`), so it is safe in the background under a read lock and in dumb mode.
// Answers Untracked for a file the manager has not scanned yet (it would call it tracked); redrawn once settled (see settledVcsTrackingOf).
internal fun vcsTrackingOf(project: Project, file: VirtualFile): VcsTracking =
    sdkCall("ask the VCS about ${file.path}") { readVcs(project, file).tracking }.getOrDefault(VcsTracking.NoVcs)

internal fun settledVcsTrackingOf(project: Project, file: VirtualFile): VcsTracking? =
    sdkCall("ask the settled VCS status of ${file.path}") { readVcs(project, file).takeIf { it.settled }?.tracking }.getOrNull()

private fun readVcs(project: Project, file: VirtualFile): VcsReading {
    val hasVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file) != null
    if (!hasVcs) return VcsReading(hasVcs = false, ignored = false, unversioned = false, scanPending = false)
    val changes = ChangeListManager.getInstance(project)
    val pending = VcsDirtyScopeManager.getInstance(project).whatFilesDirty(listOf(VcsUtil.getFilePath(file))).isNotEmpty()
    return VcsReading(hasVcs = true, ignored = changes.isIgnoredFile(file), unversioned = changes.isUnversioned(file), scanPending = pending)
}

internal data class VcsReading(val hasVcs: Boolean, val ignored: Boolean, val unversioned: Boolean, val scanPending: Boolean) {
    val tracking: VcsTracking
        get() = when {
            !hasVcs -> VcsTracking.NoVcs
            ignored -> VcsTracking.Ignored
            unversioned -> VcsTracking.Untracked
            // The manager has not scanned the file: it cannot tell a new file from a tracked one, and "tracked" is the answer it gives by default.
            scanPending -> VcsTracking.Untracked
            else -> VcsTracking.Tracked
        }

    val settled: Boolean get() = !(hasVcs && !ignored && !unversioned && scanPending)
}
