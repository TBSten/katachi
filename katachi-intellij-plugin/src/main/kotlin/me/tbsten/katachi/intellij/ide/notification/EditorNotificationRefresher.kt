package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Asks the platform to recompute the katachi notification of a file when what it depends on
 * changes behind the platform's back: the platform recomputes on opening a file and on
 * `updateAllNotifications`, not on an edit, a save or a write from outside (spike S2 (c)).
 *
 * - Edits: a [DocumentListener] (living as long as [parent]) recomputes a file that had a match,
 *   [EDIT_SETTLE_MILLIS] after its last change, so typing does not recompute on every key.
 * - Generations: each change of the project service's ledger recomputes the files it touched; a
 *   file katachi generated counts as closed with × from then on ([dismiss], decision 8).
 *
 * ```kotlin
 * refresher.followEdits(path) // after the notification found a match for path
 * ```
 */
internal class EditorNotificationRefresher(
    private val project: Project,
    private val scope: CoroutineScope,
    parent: Disposable,
    private val dismiss: (Path) -> Unit,
) {
    /** The files whose edits matter: a template fits them. */
    private val followed: MutableSet<Path> = ConcurrentHashMap.newKeySet()
    private val pending = ConcurrentHashMap<VirtualFile, Job>()
    private val ledgerFollowedFor = AtomicReference<KatachiProjectService?>(null)

    init {
        sdkCall("listen to Document edits for the katachi notification") {
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(
                object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) = onEdit(event)
                },
                parent,
            )
        }
    }

    fun followEdits(file: Path) {
        followed.add(file)
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
                    // TODO(V2 L3): "succeeded" is never cleared, so a file generated, deleted and created empty again
                    //  gets no notification in this session; clear the ledger entry when the file is deleted.
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

    private companion object {
        const val EDIT_SETTLE_MILLIS = 300L
    }
}
