package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.ui.ui
import org.junit.jupiter.api.Assertions.assertEquals
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

/**
 * Replaces the dialog's `name` field with [value] by keyboard only, starting from `name` or the field
 * right after it (`body`). Compose's focus is invisible to Driver, so a probe character tells which
 * field has it before anything is erased, and each step waits for the ViewModel to show its effect:
 * a blind Shift+Tab then Cmd+A once put the whole value at the end of `body` instead (10-2). Only
 * Shift+Tab carries a modifier; the field is cleared with Backspace, not the macOS-only Cmd+A.
 */
internal fun Driver.typeEntryCapture(value: String, bridge: EditorEntryProbe) {
    val name = entryField(bridge, "name")
    val body = entryField(bridge, "body")
    ui.keyboard { typeText(ENTRY_PROBE, delayBetweenCharsInMs = 80) }
    val landed = awaitProbe(bridge, "name" to name, "body" to body)
    if (landed == "body") {
        ui.keyboard { backspace() }
        awaitEntryDialog(bridge, "field=body=$body")
        ui.keyboard { hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_TAB) }
        ui.keyboard { typeText(ENTRY_PROBE, delayBetweenCharsInMs = 80) }
        assertEquals("name", awaitProbe(bridge, "name" to name, "body" to body), "Shift+Tab from body must reach name")
    }
    // The caret is after the probe at the end of name: erase the old value and the probe.
    ui.keyboard { repeat(name.length + ENTRY_PROBE.length) { backspace() } }
    awaitEntryDialog(bridge, "field=name=")
    ui.keyboard { typeText(value, delayBetweenCharsInMs = 80) }
    awaitEntryDialog(bridge, "field=name=$value")
}

private const val ENTRY_PROBE = "Q"

private fun entryField(bridge: EditorEntryProbe, name: String): String =
    bridge.describeDialog().lines().single { it.startsWith("field=$name=") }.removePrefix("field=$name=")

/** Which of [fields] (name to value before the probe) now ends with the probe; fails when none does. */
private fun awaitProbe(bridge: EditorEntryProbe, vararg fields: Pair<String, String>): String {
    var landed: String? = null
    waitFor("probe typed into a text field", timeout = 30.seconds, errorMessage = { bridge.describeDialog() }) {
        val lines = bridge.describeDialog().lines()
        landed = fields.firstOrNull { (name, before) -> "field=$name=$before$ENTRY_PROBE" in lines }?.first
        landed != null
    }
    return checkNotNull(landed)
}

internal fun Driver.assertEntryEditor(path: String, expected: String) {
    val editors = service(FileEditorManager::class, singleProject())
    waitFor("generated file and content in editor", timeout = 1.minutes) {
        editors.getCurrentFile().getPath() == path && editors.getSelectedTextEditor()?.getDocument()?.getText() == expected
    }
    assertEquals(expected, editors.getSelectedTextEditor()?.getDocument()?.getText())
}
