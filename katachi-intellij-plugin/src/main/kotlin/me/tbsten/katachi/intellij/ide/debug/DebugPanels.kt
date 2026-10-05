package me.tbsten.katachi.intellij.ide.debug

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.InplaceButton
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.notification.NoRolePanels
import me.tbsten.katachi.intellij.ide.sdkCall
import java.awt.Window
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.RootPaneContainer

/**
 * What a user does with the mouse on the no-role notification and its popup, done inside the IDE for the Driver smoke. Driver's
 * own click moves the real mouse, which lands in whatever window is in front when the IDE under test is not (a machine in use,
 * a locked screen); these call what the click would call (`doClick`, `cancel`) and need no window to be in front.
 *
 * ```kotlin
 * DebugPanels(project).pressNoRoleLink("Nearby roles…")   // "pressed=Nearby roles…"
 * DebugPanels(project).closePopups()                       // "closed=1"
 * ```
 */
internal class DebugPanels(private val project: Project) {
    /** Presses the link named [text] of the no-role notification on the selected editor: `pressed=<text>` or `pressed=none`. */
    fun pressNoRoleLink(text: String): String = onEdt {
        val panel = FileEditorManager.getInstance(project).selectedEditors.firstOrNull()?.let { NoRolePanels.of(it) }
        val link = panel?.let { UIUtil.findComponentsOfType(it, HyperlinkLabel::class.java).firstOrNull { label -> label.text == text } }
        if (link == null) {
            "pressed=none"
        } else {
            link.doClick()
            "pressed=$text"
        }
    }

    /** Presses × of the no-role notification on the selected editor: `dismissed=true` or `dismissed=false`. */
    fun dismissNoRole(): String = onEdt {
        val panel = FileEditorManager.getInstance(project).selectedEditors.firstOrNull()?.let { NoRolePanels.of(it) }
        // The panel has two InplaceButtons: the settings gear (a modal dialog, which would hold this call) and ×, by its tooltip.
        val tooltip = KatachiBundle.message("notification.rules.dismiss.tooltip")
        val button = panel?.let { UIUtil.findComponentsOfType(it, InplaceButton::class.java).firstOrNull { b -> b.toolTipText == tooltip } }
        button?.doClick()
        "dismissed=${button != null}"
    }

    /** Writes the first popup on screen to [out] (see [DebugScreenshots.ofPopup]): `saved=...` or `saved=none`. */
    fun screenshotPopup(out: String): String = onEdt { popups().firstOrNull()?.let { DebugScreenshots(project).ofPopup(it, out) } ?: "saved=none" }

    /**
     * The rows of the first popup on screen as the list draws them: `popup=none`, or `popup=open` and one `row=<text>|enabled or
     * disabled` each, a separator as `row=---`. Driver's text search does not see list cells, so the smoke reads them here.
     */
    fun describePopup(): String = onEdt {
        @Suppress("UNCHECKED_CAST")
        val list = popups().firstOrNull()?.let { UIUtil.findComponentsOfType(it.content, JList::class.java).firstOrNull() } as JList<Any?>?
        if (list == null) {
            "popup=none"
        } else {
            val rows = (0 until list.model.size).map { index ->
                val cell = list.cellRenderer.getListCellRendererComponent(list, list.model.getElementAt(index), index, false, false)
                val text = UIUtil.findComponentsOfType(cell as JComponent, JLabel::class.java).mapNotNull { it.text?.takeIf(String::isNotBlank) }
                val colored = UIUtil.findComponentsOfType(cell, SimpleColoredComponent::class.java).map { it.getCharSequence(true).toString() }
                val shown = (text + colored).joinToString("  ").replace("\n", " ")
                "row=${shown.ifEmpty { "---" }}|${if (cell.isEnabled) "enabled" else "disabled"}"
            }
            (listOf("popup=open") + rows).joinToString("\n")
        }
    }

    /**
     * What coming back to the IDE does after the user ran `git add` in a terminal: the platform looks at the disk again. An IDE
     * that is not in front (the smoke's, on a machine in use) never gets that activation, and reads `.git/index` only when
     * the file watcher tells it. `refreshed=true` once the refresh ended.
     */
    fun refreshFromDisk(): String = onEdt {
        sdkCall("refresh the virtual file system from the disk") { VirtualFileManager.getInstance().syncRefresh() }
        "refreshed=true"
    }

    /** Closes every popup on screen: `closed=<count>`. */
    fun closePopups(): String = onEdt {
        val found = popups()
        found.forEach { sdkCall("close a popup") { it.cancel() } }
        "closed=${found.size}"
    }

    /** The popups showing: those with a window of their own, and those drawn in a window by one of its lists. */
    private fun popups(): List<JBPopup> {
        val windows = Window.getWindows().filter { it.isShowing }
        val owned = windows.mapNotNull { PopupUtil.getPopupFor(it) }
        val inside = windows.mapNotNull { (it as? RootPaneContainer)?.rootPane }.flatMap { UIUtil.findComponentsOfType(it, JList::class.java) }.mapNotNull { PopupUtil.getPopupContainerFor(it) }
        return (owned + inside).distinct()
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        ApplicationManager.getApplication().invokeAndWait({ result = runCatching(block) }, ModalityState.any())
        return (result ?: error("the EDT did not run the block")).getOrThrow()
    }
}
