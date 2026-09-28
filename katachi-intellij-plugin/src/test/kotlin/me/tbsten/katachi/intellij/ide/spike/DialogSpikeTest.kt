package me.tbsten.katachi.intellij.ide.spike

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import me.tbsten.katachi.intellij.AnalysisTestBase
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.ui.component.Text
import java.awt.GraphicsEnvironment
import javax.swing.JComponent

/** S2 (e): a DialogWrapper whose center panel is a JewelComposePanel, created (not shown) in a headless test. */
internal class DialogSpikeTest : AnalysisTestBase() {
    private fun <T> withDialog(block: (SpikeComposeDialog) -> T): T {
        val dialog = SpikeComposeDialog(project)
        try {
            return block(dialog)
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    // covers: 論点5
    fun `test e JewelComposePanel を載せた DialogWrapper をヘッドレスで作れて OK を押せる`() {
        spike("e.headless", GraphicsEnvironment.isHeadless())
        val (title, exit) = withDialog { dialog ->
            spike("e.center", dialog.center?.javaClass?.name)
            spike("e.composedBeforeShow", dialog.composed)
            assertNotNull(dialog.center)
            assertTrue(dialog.isOKActionEnabled)
            dialog.pressOk()
            dialog.title to dialog.exitCode
        }
        assertEquals("Spike", title)
        assertEquals(DialogWrapper.OK_EXIT_CODE, exit)
    }

    // covers: 論点5
    fun `test e OK を押せなくした DialogWrapper は doOKAction で閉じない`() {
        val closed = withDialog { dialog ->
            dialog.isOKActionEnabled = false
            dialog.pressOk()
            dialog.isDisposed
        }
        assertFalse(closed)
    }

    // covers: 論点5
    fun `test e ヘッドレスでは Compose の中身は組み立てられない`() {
        withDialog { dialog ->
            var thrown: Throwable? = null
            try {
                dialog.center?.addNotify()
            } catch (e: Throwable) {
                thrown = e
            }
            spike("e.addNotify.thrown", thrown?.let { "${it.javaClass.name}: ${it.message}" })
            spike("e.composedAfterAddNotify", dialog.composed)
        }
    }
}

internal class SpikeComposeDialog(project: Project) : DialogWrapper(project) {
    @Volatile var composed: Boolean = false
    var center: JComponent? = null

    init {
        title = "Spike"
        init()
    }

    /** DialogWrapper.doOKAction is protected; a test hook as ConflictDialog.continueWith. */
    fun pressOk() = doOKAction()

    override fun createCenterPanel(): JComponent = JewelComposePanel {
        composed = true
        Text("spike")
    }.also { center = it }
}
