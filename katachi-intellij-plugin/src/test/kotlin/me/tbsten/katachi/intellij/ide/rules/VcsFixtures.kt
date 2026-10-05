package me.tbsten.katachi.intellij.ide.rules

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManagerGate
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.ChangelistBuilder
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.vcsUtil.VcsUtil
import java.nio.file.Path

/** A VCS that tracks only what [tracks] says: every other file of its dirty scope is "not under version control". */
internal open class TrackingSome(private val tracks: (VirtualFile) -> Boolean) : ChangeProvider {
    override fun getChanges(dirtyScope: VcsDirtyScope, builder: ChangelistBuilder, progress: ProgressIndicator, addGate: ChangeListManagerGate) {
        for (dirty in dirtyScope.recursivelyDirtyDirectories + dirtyScope.dirtyFiles) {
            val start = dirty.virtualFile ?: continue
            VfsUtilCore.visitChildrenRecursively(
                start,
                object : VirtualFileVisitor<Any>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (!file.isDirectory && !tracks(file)) builder.processUnversionedFile(VcsUtil.getFilePath(file))
                        return true
                    }
                },
            )
        }
    }

    override fun isModifiedDocumentTrackingRequired(): Boolean = false
}

/** A VCS that tracks nothing: every file of its dirty scope is "not under version control". */
internal class EverythingUntracked : TrackingSome({ false })

/**
 * Runs [block] with [root] under a VCS that tracks nothing (so a new file there is "unversioned", as before `git add`), and
 * the platform's change list manager up to date. The VCS is removed afterwards. [block] gets a function that ends the
 * mapping, as `git init` undone would.
 */
internal fun withUntrackingVcs(project: Project, root: Path, block: (endMapping: () -> Unit) -> Unit) =
    withVcs(project, root, EverythingUntracked(), block)

/** Like [withUntrackingVcs], over [changeProvider]: a VCS that tracks the files it says (the ones it does not report as unversioned). */
internal fun withVcs(project: Project, root: Path, changeProvider: ChangeProvider, block: (endMapping: () -> Unit) -> Unit) {
    val vcs = MockAbstractVcs(project)
    vcs.changeProvider = changeProvider
    val manager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
    manager.registerVcs(vcs)
    try {
        manager.setDirectoryMapping(root.toString(), vcs.name)
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
        ChangeListManagerImpl.getInstanceImpl(project).waitEverythingDoneInTestMode()
        block { manager.setDirectoryMappings(emptyList()) }
    } finally {
        manager.setDirectoryMappings(emptyList())
        manager.unregisterVcs(vcs)
    }
}
