package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.ui.ui
import org.junit.jupiter.api.Assertions.assertEquals
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

internal fun Driver.assertEntryEditor(path: String, expected: String) {
    val editors = service(FileEditorManager::class, singleProject())
    waitFor("generated file and content in editor", timeout = 1.minutes) {
        editors.getCurrentFile().getPath() == path && editors.getSelectedTextEditor()?.getDocument()?.getText() == expected
    }
    assertEquals(expected, editors.getSelectedTextEditor()?.getDocument()?.getText())
}
