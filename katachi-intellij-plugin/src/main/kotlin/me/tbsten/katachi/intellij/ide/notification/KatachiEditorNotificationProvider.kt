package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

/**
 * The editor notification on files a katachi template fits (design section 1): [Create] on an empty
 * one, [View template] on one with content, with ⚙ and ×.
 *
 * [collectNotificationData] runs in the background under a read lock (`@RequiresReadLock` in 261), so
 * the whole decision is made there: settings, the index state, the matches, the empty-file rule over
 * the cached Document or the disk, the memory and the ledger, then `decideNotification`. It returns
 * [EditorNotificationProvider.CONST_NULL] for no notification. The returned function only builds the
 * panel on the EDT. Without an index it calls `KatachiProjectService.ensureLoaded()` and shows nothing.
 *
 * ```xml
 * <editorNotificationProvider implementation="me.tbsten.katachi.intellij.ide.notification.KatachiEditorNotificationProvider"/>
 * ```
 */
internal class KatachiEditorNotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        // TODO(E1)
        return EditorNotificationProvider.CONST_NULL
    }
}
