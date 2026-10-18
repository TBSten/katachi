package me.tbsten.katachi.intellij.ide

import com.intellij.testFramework.replaceService
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.notification.NotificationPorts

/**
 * The two real ports ([NotificationPorts.of], [DefinitionPorts.of]) ask [DefinitionModulePresence], not the synced data
 * themselves: the notification and the definition decide at every highlight and every editor, and a walk of the synced data each time is the cost E-4 removed.
 */
internal class DefinitionModulePresenceWiringTest : AnalysisTestBase() {
    private var reads = 0

    override fun setUp() {
        super.setUp()
        val counting = DefinitionModulePresence(project) { reads++; true }
        project.replaceService(DefinitionModulePresence::class.java, counting, testRootDisposable)
    }

    fun `test 通知と定義のポートは何度聞いても同期データを読むのは1回だけ`() {
        val notification = NotificationPorts.of(project)
        val definition = DefinitionPorts.of(project)

        repeat(3) { assertTrue(notification.hasDefinitionModule()) }
        repeat(3) { assertTrue(definition.hasDefinitionModule()) }

        assertEquals("both ports share the one kept answer", 1, reads)
    }

    fun `test 定義のポートだけでも同期データを読むのは1回だけ`() {
        val definition = DefinitionPorts.of(project)

        repeat(3) { assertTrue(definition.hasDefinitionModule()) }

        assertEquals("the definition port asks the kept answer", 1, reads)
    }
}
