package me.tbsten.katachi.intellij

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowEP
import me.tbsten.katachi.intellij.ide.KatachiToolWindowFactory

/** plugin.xml registers the tool window the screen spec names, with this plugin's factory. */
internal class KatachiToolWindowRegistrationTest : AnalysisTestBase() {

    fun `test katachi の Tool Window が右側に登録されている`() {
        val registered = ToolWindowEP.EP_NAME.extensionList.filter { it.id == KatachiToolWindowFactory.TOOL_WINDOW_ID }

        assertEquals("tool windows with id `katachi`: $registered", 1, registered.size)
        val toolWindow = registered.single()
        assertEquals(KatachiToolWindowFactory::class.java.name, toolWindow.factoryClass)
        assertEquals(ToolWindowAnchor.RIGHT.toString(), toolWindow.anchor)
    }
}
