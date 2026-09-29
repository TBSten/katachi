package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal fun Driver.entryNotification() = ui.x { byClass("KatachiNotificationPanel") }

/**
 * The panel truncates its message ("You can create this fr...") in the narrow editor, so the visible
 * text alone cannot tell the two variants apart. [link] (the single action link) is short and does.
 */
internal fun Driver.awaitEntryNotification(link: String) {
    waitFor("katachi notification with link: $link", timeout = 3.minutes) {
        entryNotification().present() && entryNotification().x { byVisibleText(link) }.present()
    }
}

internal fun awaitEntryDialog(bridge: EditorEntryProbe, vararg lines: String) {
    waitFor("generate dialog: ${lines.toList()}", timeout = 30.seconds, errorMessage = { bridge.describeDialog() }) {
        bridge.describeDialog().lines().containsAll(lines.toList())
    }
}

internal fun Driver.typeEntryCapture(value: String, bridge: EditorEntryProbe) {
    // The caller moves focus with Tab/Shift+Tab; never click inside Compose by coordinates.
    ui.keyboard {
        hotKey(KeyEvent.VK_META, KeyEvent.VK_A)
        typeText(value, delayBetweenCharsInMs = 80)
    }
    awaitEntryDialog(bridge, "field=name=$value")
}

/** S3's select-opened-file locator avoids assumptions about compact package nodes in Project. */
internal fun Driver.openEntryNewMenu(file: String) {
    openFile(file, waitForCodeAnalysis = false)
    // The Project toolbar (Select Opened File) only exists while the tool window is active, and the
    // editor holds focus after a dialog closed. Activate it with the platform action, not by a click.
    invokeAction("ActivateProjectToolWindow", now = false)
    val view = ideFrame().projectView()
    view.selectOpenedFile()
    val tree = view.projectViewTree
    tree.waitForNodesLoaded()
    waitFor("opened file selected in Project", timeout = 30.seconds, errorMessage = { "selected=" + tree.collectSelectedPaths().map { it.path } }) {
        tree.collectSelectedPaths().singleOrNull()?.path?.lastOrNull()
            .let { it == file.substringAfterLast('/') || it == file.substringAfterLast('/').removeSuffix(".kt") } // Kotlin nodes hide ".kt"
    }
    // Right-click the selected file itself: the directory rows of a compact tree cannot be matched
    // by name, and IdeView.getDirectories() (what New uses) answers the file's directory.
    val leaf = tree.collectSelectedPaths().single().path.last()
    tree.rightClickRow { it.startsWith(leaf) }
    val menu = ideFrame().popupMenu()
    val before = menu.itemsList()
    assertTrue("New" in before, before.toString())
    menu.findMenuItemByText("New").click()
    waitFor("New submenu loaded", timeout = 10.seconds) { ideFrame().popupMenu().itemsList().size > before.size }
}

internal fun Driver.selectEntryMenuItem(text: String) {
    waitFor("menu item $text", timeout = 10.seconds, errorMessage = { ideFrame().popupMenu().itemsList().toString() }) {
        text in ideFrame().popupMenu().itemsList()
    }
    ideFrame().popupMenu().findMenuItemByText(text).click()
}

internal fun Driver.assertEntryEditor(path: String, expected: String) {
    val editors = service(FileEditorManager::class, singleProject())
    waitFor("generated file and content in editor", timeout = 1.minutes) {
        editors.getCurrentFile().getPath() == path && editors.getSelectedTextEditor()?.getDocument()?.getText() == expected
    }
    assertEquals(expected, editors.getSelectedTextEditor()?.getDocument()?.getText())
}
