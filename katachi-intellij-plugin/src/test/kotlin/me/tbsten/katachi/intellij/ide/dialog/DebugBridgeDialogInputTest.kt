package me.tbsten.katachi.intellij.ide.dialog

import com.intellij.openapi.util.Disposer
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge
import me.tbsten.katachi.intellij.testing.underRoot

/**
 * `KatachiDebugBridge.fillDialog` and `pressDialog`: the String door the Driver smoke types into and presses the modal dialog
 * through. What `describeDialog` says is in [DebugBridgeDialogTest]; here the dialog is changed and read back through it.
 */
internal class DebugBridgeDialogInputTest : AnalysisTestBase() {
    private val world = DialogWorld()

    private fun <T> withBridge(block: (KatachiDebugBridge) -> T): T {
        val previous = System.getProperty("katachi.debugBridge")
        System.setProperty("katachi.debugBridge", "true")
        try {
            return block(KatachiDebugBridge(project))
        } finally {
            if (previous == null) System.clearProperty("katachi.debugBridge") else System.setProperty("katachi.debugBridge", previous)
        }
    }

    private fun <T> withShowing(block: (KatachiGenerateDialog) -> T): T {
        val dialog = world.dialog(project)
        try {
            GenerateDialogs.setShowingForTest(dialog, testRootDisposable)
            return block(dialog)
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    fun `test fillDialog は 表示中のダイアログの欄に入れ describeDialog に出る`() = withShowing {
        assertEquals("filled=name", withBridge { it.fillDialog("name", "Profile") })

        val lines = withBridge { it.describeDialog() }.lines()
        assertTrue(lines.toString(), "field=name=Profile" in lines)
    }

    fun `test fillDialog は ダイアログが無い 欄が無いときは none`() {
        assertEquals("filled=none", withBridge { it.fillDialog("name", "x") })
        withShowing {
            assertEquals("filled=none", withBridge { it.fillDialog("no-such-field", "x") })
        }
    }

    fun `test 理由が出ている状態で fillDialog すると describeDialog から refusal が消える`() = withShowing { dialog ->
        withBridge { it.fillDialog("name", "Profile") }
        withBridge { it.fillDialog("title", "Profile") }
        world.existing[underRoot("feature/home/ui/ProfileScreen.kt")] = TargetState.HasContent
        dialog.pressGenerate()
        assertNotNull(dialog.refusal)
        assertTrue(withBridge { it.describeDialog() }.lines().any { it.startsWith("refusal=") })

        assertEquals("filled=name", withBridge { it.fillDialog("name", "Detail") })

        val lines = withBridge { it.describeDialog() }.lines()
        assertTrue(lines.toString(), lines.none { it.startsWith("refusal=") })
    }

    fun `test pressDialog の generate は OK が使えるときだけ押され 使えないときは none`() = withShowing { dialog ->
        assertFalse(dialog.isOKActionEnabled)
        assertEquals("pressed=none", withBridge { it.pressDialog("generate") })
        assertNull("nothing was generated", dialog.result)

        withBridge { it.fillDialog("name", "Profile") }
        withBridge { it.fillDialog("title", "Profile") }
        assertTrue(withBridge { it.describeDialog() }, dialog.isOKActionEnabled)

        assertEquals("pressed=generate", withBridge { it.pressDialog("generate") })
        assertNotNull("Generate asked for the request the fields hold", dialog.result)
    }

    fun `test pressDialog の cancel は 閉じて 結果を残さない 知らないボタンは none`() = withShowing { dialog ->
        assertEquals("pressed=none", withBridge { it.pressDialog("ok") })

        assertEquals("pressed=cancel", withBridge { it.pressDialog("cancel") })
        assertNull(dialog.result)
        assertTrue("the dialog is closed", dialog.isDisposed)
    }

    fun `test pressDialog は ダイアログが無いときは none`() {
        assertEquals("pressed=none", withBridge { it.pressDialog("generate") })
        assertEquals("pressed=none", withBridge { it.pressDialog("cancel") })
    }
}
