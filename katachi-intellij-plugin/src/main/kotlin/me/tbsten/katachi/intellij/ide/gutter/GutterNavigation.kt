package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiNotifications
import me.tbsten.katachi.intellij.ide.sdkCall
import java.nio.file.Path

/**
 * Where the gutter menu's rows lead (design 2.3): a line of a definition's file, or a file of the project. A file that is
 * not there (the definition is older than the disk) is told in a balloon instead of failing the click.
 *
 * Called on the EDT. Every SDK call is in `sdkCall`.
 *
 * ```kotlin
 * GutterNavigation(project, ports.notify).openLine(serviceRoleKt, line = 80)
 * ```
 */
internal class GutterNavigation(private val project: Project, private val notify: (String) -> Unit) {
    /** Opens [file] with the caret at [line] (1-based) and the editor focused. */
    fun openLine(file: VirtualFile, line: Int) {
        sdkCall("open ${file.path}:$line") { OpenFileDescriptor(project, file, (line - 1).coerceAtLeast(0), 0).navigate(true) }
            .onFailure { notify(KatachiBundle.message("gutter.failed", it.message.orEmpty())) }
    }

    /** Opens the project file at [path] (absolute), with the caret at [line] (1-based) when given; a balloon says so when there is none. */
    fun openFile(path: Path, line: Int? = null) {
        // Refreshed: the file may have been made or deleted after the VFS last looked, and a sibling the definition lists can be either.
        val file = sdkCall("find $path") {
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)?.also { it.refresh(false, false) }?.takeIf { it.isValid && it.exists() }
        }.getOrNull()
        if (file == null) {
            notify(KatachiBundle.message("gutter.notFound", path.fileName?.toString() ?: path.toString()))
            return
        }
        sdkCall("open ${file.path}") {
            (if (line == null) OpenFileDescriptor(project, file) else OpenFileDescriptor(project, file, (line - 1).coerceAtLeast(0), 0)).navigate(true)
        }.onFailure { notify(KatachiBundle.message("gutter.failed", it.message.orEmpty())) }
    }

    companion object {
        /** A warning in the `katachi` notification group. */
        fun balloon(project: Project, text: String) {
            sdkCall("show the balloon \"$text\"") {
                NotificationGroupManager.getInstance().getNotificationGroup(KatachiNotifications.GROUP_ID)
                    .createNotification(text, NotificationType.WARNING)
                    .notify(project)
            }
        }
    }
}
