package me.tbsten.katachi.intellij.ide.dialog

import com.intellij.openapi.util.Disposer
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogIntent

/** `KatachiDebugBridge.describeDialog`, the String door the Driver smoke reads the modal dialog through. */
internal class DebugBridgeDialogTest : AnalysisTestBase() {
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

    // covers: 論点17
    fun `test ダイアログが出ていないときは none を返す`() {
        assertEquals("dialog=none", withBridge { it.describeDialog() })
    }

    // covers: 論点17
    fun `test 出ているダイアログの題と選択中のテンプレートと入力と生成できるかを返す`() {
        val dialog = world.dialog(project)
        try {
            GenerateDialogs.setShowingForTest(dialog, testRootDisposable)
            dialog.viewModel.dispatch(GenerateDialogIntent.Input("name", "Profile"))

            val lines = withBridge { it.describeDialog() }.lines()

            assertEquals("dialog=open", lines.first())
            assertTrue(lines.toString(), "template=ui.Screen" in lines)
            assertTrue(lines.toString(), "field=feature=home" in lines)
            assertTrue(lines.toString(), "field=name=Profile" in lines)
            assertTrue(lines.toString(), "field=title=" in lines)
            assertTrue(lines.toString(), "canGenerate=false" in lines)
            assertTrue(lines.toString(), "okEnabled=false" in lines)
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    // covers: 論点17
    fun `test デバッグ用の入口が無効なときは読めない`() {
        assertThrows(IllegalStateException::class.java) { KatachiDebugBridge(project).describeDialog() }
    }
}
