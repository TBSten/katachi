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

// Called on the EDT; every SDK call goes through `sdkCall`.
internal class GutterNavigation(private val project: Project, private val notify: (String) -> Unit) {
    fun openLine(file: VirtualFile, line: Int) {
        sdkCall("open ${file.path}:$line") { OpenFileDescriptor(project, file, (line - 1).coerceAtLeast(0), 0).navigate(true) }
            .onFailure { notify(KatachiBundle.message("gutter.failed", it.message.orEmpty())) }
    }

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
        fun balloon(project: Project, text: String) {
            sdkCall("show the balloon \"$text\"") {
                NotificationGroupManager.getInstance().getNotificationGroup(KatachiNotifications.GROUP_ID)
                    .createNotification(text, NotificationType.WARNING)
                    .notify(project)
            }
        }
    }
}
