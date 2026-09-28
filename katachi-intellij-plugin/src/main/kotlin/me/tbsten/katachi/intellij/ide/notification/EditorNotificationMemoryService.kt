package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * The editor notification's [EditorNotificationMemory] for one project session (issue 15): what was
 * closed with ×, and which files already had their "has content" notification. Memory only: a new
 * service (a reopened project) remembers nothing.
 *
 * "Shown once" is recorded when the last editor of a file closes while its panel says "a template
 * fits" (A3's proposal): recording it when the panel is built would hide the panel at its next
 * recomputation, while the file is still open. It also owns the [EditorNotificationRefresher] that
 * recomputes a file's notification when its Document or its generation changes.
 *
 * Created by the notification only once a katachi definition module is known (decision 17).
 *
 * ```kotlin
 * project.service<EditorNotificationMemoryService>().dismiss(path) // × on path's notification
 * ```
 */
@Service(Service.Level.PROJECT)
internal class EditorNotificationMemoryService(
    private val project: Project,
    scope: CoroutineScope,
) : Disposable {
    private val memory = AtomicReference(EditorNotificationMemory.EMPTY)

    /** The files whose latest decision was "a template fits": closing them counts as shown. */
    private val showingContentNotice: MutableSet<Path> = ConcurrentHashMap.newKeySet()

    private val refresher = EditorNotificationRefresher(project, scope, this, ::dismiss)

    init {
        sdkCall("listen to closed editors for the katachi notification") {
            project.messageBus.connect(this).subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                        // Still open in another window or split: not closed yet.
                        if (sdkCall("ask whether ${file.path} is still open") { source.isFileOpen(file) }.getOrNull() == true) return
                        file.fileSystem.getNioPath(file)?.let(::fileClosed)
                    }
                },
            )
        }
    }

    /** The memory as of now, for one decision. */
    val current: EditorNotificationMemory get() = memory.get()

    /** × on [file]'s notification, and a file katachi just generated (decision 8). */
    fun dismiss(file: Path) {
        memory.updateAndGet { it.dismiss(file) }
    }

    /** What the notification decided for [file] last, so that closing it knows whether it was shown. */
    fun recordDecision(file: Path, decision: NotificationDecision) {
        if (decision is NotificationDecision.ViewTemplate) showingContentNotice.add(file) else showingContentNotice.remove(file)
    }

    /** The last editor of [file] closed. */
    fun fileClosed(file: Path) {
        if (showingContentNotice.remove(file)) memory.updateAndGet { it.markContentNoticeShown(file) }
    }

    /** Recomputes the notification of [file] (a path with a match) a moment after its Document changes (S2 (c)). */
    fun followEdits(file: Path) = refresher.followEdits(file)

    /** Recomputes the notifications of the files [service]'s generations touch; once per service. */
    fun followGenerations(service: KatachiProjectService) = refresher.followGenerations(service)

    override fun dispose() = Unit
}
