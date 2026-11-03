package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

// Weak on both sides: it must never keep an editor or a panel alive.
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
