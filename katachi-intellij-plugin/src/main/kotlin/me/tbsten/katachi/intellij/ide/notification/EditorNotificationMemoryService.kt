package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

// "Shown once" is recorded when the last editor of a file closes: recording it when the panel is built would hide the panel at its next recomputation while the file is open.
@Service(Service.Level.PROJECT)
internal class EditorNotificationMemoryService(
    private val project: Project,
    scope: CoroutineScope,
) : Disposable {
    private val memory = AtomicReference(EditorNotificationMemory.EMPTY)

    private val showingContentNotice: MutableSet<Path> = ConcurrentHashMap.newKeySet()

    private val refresher = EditorNotificationRefresher(project, scope, this, ::dismiss, ::forgetDismissed)

    init {
        sdkCall("listen to closed editors for the katachi notification") {
            project.messageBus.connect(this).subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                        // Still open in another window or split: not closed yet.
                        if (sdkCall("ask whether ${file.path} is still open") { source.isFileOpen(file) }.getOrNull() == true) return
                        refresher.unfollowVcs(file)
                        sdkCall("find the path of ${file.name}") { file.fileSystem.getNioPath(file) }.getOrNull()?.let(::fileClosed)
                    }
                },
            )
        }
    }

    val current: EditorNotificationMemory get() = memory.get()

    fun forgetDismissed(file: Path) {
        memory.updateAndGet { it.forgetDismissed(file) }
    }

    fun dismiss(file: Path) {
        memory.updateAndGet { it.dismiss(file) }
    }

    fun dismissNoRole(file: Path) {
        memory.updateAndGet { it.dismissNoRole(file) }
    }

    fun recordDecision(file: Path, decision: NotificationDecision) {
        if (decision is NotificationDecision.ViewTemplate) showingContentNotice.add(file) else showingContentNotice.remove(file)
    }

    fun fileClosed(file: Path) {
        if (showingContentNotice.remove(file)) memory.updateAndGet { it.markContentNoticeShown(file) }
    }

    fun followEdits(file: Path) = refresher.followEdits(file)

    fun followDefinitions(service: DefinitionModelService, observed: DefinitionState) = refresher.followDefinitions(service, observed)

    fun followVcs(file: VirtualFile, seen: VcsTracking, now: () -> VcsTracking?) = refresher.followVcs(file, seen, now)

    fun unfollowVcs(file: VirtualFile) = refresher.unfollowVcs(file)

    fun followGenerations(service: KatachiProjectService) = refresher.followGenerations(service)

    override fun dispose() = Unit
}
