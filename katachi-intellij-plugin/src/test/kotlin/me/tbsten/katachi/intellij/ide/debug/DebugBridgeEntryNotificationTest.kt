package me.tbsten.katachi.intellij.ide.debug

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorNotificationPanel
import me.tbsten.katachi.intellij.ide.notification.KatachiNotificationPanel

/**
 * The door of `KatachiDebugBridge` that stands in for a click on the template notification, where Driver's real mouse does not
 * reach the IDE under test on a local macOS: `pressEntryLinkLater`.
 */
internal class DebugBridgeEntryNotificationTest : BasePlatformTestCase() {
    private val pressed = mutableListOf<String>()

    private fun <T> withBridge(block: (KatachiDebugBridge) -> T): T {
        val previous = System.getProperty("katachi.debugBridge")
        System.setProperty("katachi.debugBridge", "true")
        try {
            return block(KatachiDebugBridge(project))
        } finally {
            if (previous == null) System.clearProperty("katachi.debugBridge") else System.setProperty("katachi.debugBridge", previous)
        }
    }

    private fun openEditor(): FileEditor {
        myFixture.configureByText("EntryEmptyService.kt", "package a\n")
        return FileEditorManager.getInstance(project).getEditors(myFixture.file.virtualFile).first()
    }

    /** A template notification inside the editor's own component: the test editor manager draws its top components nowhere. */
    private fun addEntryNotification(editor: FileEditor, link: String) {
        val panel = KatachiNotificationPanel(editor, EditorNotificationPanel.Status.Info)
        panel.createActionLabel(link) { pressed += link }
        panel.addSettingsButton { pressed += "settings" }
        editor.component.add(panel)
    }

    fun `test pressEntryLinkLater はテンプレートの通知のリンクを後から押す`() {
        addEntryNotification(openEditor(), "Create")

        val answer = withBridge { it.pressEntryLinkLater("Create") }
        assertEquals("pressed=Create", answer)
        // Queued, not done during the call: Create opens a modal dialog, which would hold it.
        assertEquals(emptyList<String>(), pressed)

        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf("Create"), pressed)
    }

    fun `test pressEntryLinkLater は歯車をツールチップで押す`() {
        addEntryNotification(openEditor(), "View Template")

        assertEquals("pressed=Open the notification settings", withBridge { it.pressEntryLinkLater("Open the notification settings") })
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(listOf("settings"), pressed)
    }

    fun `test pressEntryLinkLater は無いリンクと katachi でない通知に pressed=none を返す`() {
        val editor = openEditor()
        EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).also { other ->
            other.createActionLabel("Create") { pressed += "other" }
            editor.component.add(other)
        }

        withBridge { bridge ->
            assertEquals("pressed=none", bridge.pressEntryLinkLater("Create"))
            addEntryNotification(editor, "View Template")
            assertEquals("pressed=none", bridge.pressEntryLinkLater("Create"))
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(emptyList<String>(), pressed)
    }

    fun `test pressEntryLinkLater はデバッグ用の入口が無効なときは押さない`() {
        addEntryNotification(openEditor(), "Create")

        assertThrows(IllegalStateException::class.java) { KatachiDebugBridge(project).pressEntryLinkLater("Create") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(emptyList<String>(), pressed)
    }
}
