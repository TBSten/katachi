package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * The no-role panel each editor shows now, for [me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge.describeNoRole]: the
 * platform keeps its panels to itself, and the Swing tree they hang in differs between the IDE and the headless tests.
 * A panel is entered when built and every editor of a file is dropped when the provider decides the file has none, so it
 * says what the platform shows. Weak on both sides: it never keeps an editor or a panel alive.
 *
 * ```kotlin
 * NoRolePanels.register(panel)         // when the panel is built
 * NoRolePanels.of(editor)?.lines       // what the bridge reports
 * ```
 */
internal object NoRolePanels {
    private val panels: MutableMap<FileEditor, WeakReference<NoRolePanel>> = Collections.synchronizedMap(WeakHashMap())

    fun register(panel: NoRolePanel) {
        panels[panel.owner] = WeakReference(panel)
    }

    fun clear(file: VirtualFile) {
        synchronized(panels) { panels.keys.removeAll { it.file == file } }
    }

    fun of(editor: FileEditor): NoRolePanel? = panels[editor]?.get()
}
