package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

// The platform recomputes a notification only on opening a file and `updateAllNotifications`, not on an edit, a save or an outside write: this follows those changes.
internal class EditorNotificationRefresher(
    private val project: Project,
    private val scope: CoroutineScope,
    parent: Disposable,
    private val dismiss: (Path) -> Unit,
    private val forgetDismissed: (Path) -> Unit,
) {
    private val followed: MutableSet<Path> = ConcurrentHashMap.newKeySet()
    private val pending = ConcurrentHashMap<VirtualFile, Job>()
    private val ledgerFollowedFor = AtomicReference<KatachiProjectService?>(null)
    private val definitionFollowedFor = AtomicReference<DefinitionModelService?>(null)

    private val vcsFollowed = ConcurrentHashMap<VirtualFile, VcsFollow>()

    private class VcsFollow(val seen: VcsTracking, val now: () -> VcsTracking?)

    init {
        sdkCall("listen to Document edits for the katachi notification") {
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(
                object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) = onEdit(event)
                },
                parent,
            )
        }
        sdkCall("listen to VCS status changes for the katachi notification") {
            FileStatusManager.getInstance(project).addFileStatusListener(
                object : FileStatusListener {
                    override fun fileStatusesChanged() = vcsFollowed.keys.forEach(::recomputeIfVcsChanged)

                    override fun fileStatusChanged(virtualFile: VirtualFile) = recomputeIfVcsChanged(virtualFile)
                },
                parent,
            )
        }
        sdkCall("listen to renames and moves for the katachi notification") {
            project.messageBus.connect(parent).subscribe(
                VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        forgetDeletedGenerations(events)
                        recomputeRenamed(events)
                    }
                },
            )
        }
    }

    fun followEdits(file: Path) {
        followed.add(file)
    }

    // The platform also reports statuses that change nothing here (an edit makes a file "modified") and the VCS settling after open: those must draw nothing again.
    fun followVcs(file: VirtualFile, seen: VcsTracking, now: () -> VcsTracking?) {
        vcsFollowed[file] = VcsFollow(seen, now)
    }

    fun unfollowVcs(file: VirtualFile) {
        vcsFollowed.remove(file)
    }

    private fun recomputeRenamed(events: List<VFileEvent>) {
        val moved = events.mapNotNull { event ->
            when {
                event is VFileMoveEvent -> event.file
                event is VFilePropertyChangeEvent && event.isRename -> event.file
                else -> null
            }
        }
        if (moved.isEmpty()) return
        val open = sdkCall("list the open files") { FileEditorManager.getInstance(project).openFiles.toList() }.getOrNull() ?: return
        open.filter { file -> moved.any { VfsUtilCore.isAncestor(it, file, false) } }.forEach(::recompute)
    }

    // A failed or running entry stays: that generation is not over.
    private fun forgetDeletedGenerations(events: List<VFileEvent>) {
        val ledger = ledgerFollowedFor.get()?.ledger ?: return
        val deleted = events.filterIsInstance<VFileDeleteEvent>().mapNotNull { sdkCall("read the path of a deleted file") { Path.of(it.path) }.getOrNull() }
        if (deleted.isEmpty()) return
        for ((path, entry) in ledger.entries.value) {
            if (entry !is LedgerEntry.Succeeded || deleted.none { path.startsWith(it) }) continue
            ledger.clear(path)
            forgetDismissed(path)
        }
    }

    private fun recomputeIfVcsChanged(file: VirtualFile) {
        val follow = vcsFollowed[file] ?: return
        val now = sdkCall("read the VCS status of ${file.path}") { follow.now() }.getOrNull() ?: return
        if (now != follow.seen) recompute(file)
    }

    // `collectLatest` drops a pending redraw when a newer state arrives, so compare with what was last drawn, not with the state before it.
    fun followDefinitions(service: DefinitionModelService, observed: DefinitionState) {
        val previous = definitionFollowedFor.get()
        if (previous === service || !definitionFollowedFor.compareAndSet(previous, service)) return
        scope.launch {
            var first = true
            var drawn = observed.shown()
            service.state.collectLatest { now ->
                val isFirst = first
                first = false
                if (isFirst && now == observed) return@collectLatest
                if (now is DefinitionState.Loading && now.shown() == drawn) return@collectLatest
                delay(DEFINITION_SETTLE_MILLIS)
                sdkCall("update the katachi notifications for the definition") {
                    if (!project.isDisposed) EditorNotifications.getInstance(project).updateAllNotifications()
                }
                drawn = now.shown()
            }
        }
    }

    fun followGenerations(service: KatachiProjectService) {
        val previous = ledgerFollowedFor.get()
        if (previous === service || !ledgerFollowedFor.compareAndSet(previous, service)) return
        service.scope.launch {
            var before = emptyMap<Path, LedgerEntry>()
            service.ledger.entries.collect { now ->
                val changed = (before.keys + now.keys).filter { before[it] != now[it] }
                before = now
                for (path in changed) {
                    if (now[path] is LedgerEntry.Succeeded) dismiss(path)
                    sdkCall("find $path for its katachi notification") { LocalFileSystem.getInstance().findFileByNioFile(path) }
                        .getOrNull()
                        ?.let(::recompute)
                }
            }
        }
    }

    private fun onEdit(event: DocumentEvent) {
        val file = sdkCall("find the file of an edited Document") { FileDocumentManager.getInstance().getFile(event.document) }.getOrNull() ?: return
        val path = sdkCall("find the path of ${file.name}") { file.fileSystem.getNioPath(file) }.getOrNull() ?: return
        if (path !in followed) return
        val job = scope.launch {
            delay(EDIT_SETTLE_MILLIS)
            recompute(file)
        }
        pending.put(file, job)?.cancel()
        job.invokeOnCompletion { pending.remove(file, job) }
    }

    private fun recompute(file: VirtualFile) {
        sdkCall("update the katachi notification of ${file.path}") {
            if (!project.isDisposed) EditorNotifications.getInstance(project).updateNotifications(file)
        }
    }

    // `Loading(previous)` keeps the previous answer's freshness, so `Unavailable(previous)` into `Loading(previous)` changes the wording and is drawn again.
    private fun DefinitionState.shown(): Pair<DefinitionState.Ready?, Boolean> = answer to (this is DefinitionState.Unavailable)

    private companion object {
        const val EDIT_SETTLE_MILLIS = 300L
        const val DEFINITION_SETTLE_MILLIS = 200L
    }
}
