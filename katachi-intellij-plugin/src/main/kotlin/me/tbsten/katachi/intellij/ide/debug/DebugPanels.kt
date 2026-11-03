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
import me.tbsten.katachi.intellij.ide.notification.KatachiNotificationPanel
import me.tbsten.katachi.intellij.ide.notification.NoRolePanel
import me.tbsten.katachi.intellij.ide.notification.NoRolePanels
import me.tbsten.katachi.intellij.ide.sdkCall
import java.awt.Window
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.RootPaneContainer

internal class DebugPanels(private val project: Project) {
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

    fun pressEntryLinkLater(text: String): String = onEdt {
        val file = FileEditorManager.getInstance(project).selectedEditors.firstOrNull()?.file
        val panel = file?.let { GutterDebugReader(project).bannerPanelsOf(it) }.orEmpty()
            .firstOrNull { it is KatachiNotificationPanel && it !is NoRolePanel }
        val link = panel?.let { UIUtil.findComponentsOfType(it, HyperlinkLabel::class.java).firstOrNull { label -> label.text == text } }
        val button = panel?.let { UIUtil.findComponentsOfType(it, InplaceButton::class.java).firstOrNull { b -> b.toolTipText == text } }
        val press: (() -> Unit)? = link?.let { { it.doClick() } } ?: button?.let { { it.doClick() } }
        if (press == null) {
            "pressed=none"
        } else {
            ApplicationManager.getApplication().invokeLater({ sdkCall("press $text of the katachi notification") { press() } }, ModalityState.any())
            "pressed=$text"
        }
    }

    fun dismissNoRole(): String = onEdt {
        val panel = FileEditorManager.getInstance(project).selectedEditors.firstOrNull()?.let { NoRolePanels.of(it) }
        // The panel has two InplaceButtons: the settings gear (a modal dialog, which would hold this call) and ×, by its tooltip.
        val tooltip = KatachiBundle.message("notification.rules.dismiss.tooltip")
        val button = panel?.let { UIUtil.findComponentsOfType(it, InplaceButton::class.java).firstOrNull { b -> b.toolTipText == tooltip } }
        button?.doClick()
        "dismissed=${button != null}"
    }

    fun screenshotPopup(out: String): String = onEdt { popups().firstOrNull()?.let { DebugScreenshots(project).ofPopup(it, out) } ?: "saved=none" }

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

    fun refreshFromDisk(): String = onEdt {
        sdkCall("refresh the virtual file system from the disk") { VirtualFileManager.getInstance().syncRefresh() }
        "refreshed=true"
    }

    fun closePopups(): String = onEdt {
        val found = popups()
        found.forEach { sdkCall("close a popup") { it.cancel() } }
        "closed=${found.size}"
    }

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
