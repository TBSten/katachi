package me.tbsten.katachi.intellij.ide.debug

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert

/**
 * The door of `KatachiDebugBridge` that stands in for Driver's `invokeAction`, which takes its context from the focused window: the IDE
 * under test is not always in front, and the action then has no project. `performAction` gives the action the project itself.
 */
internal class DebugBridgePerformActionTest : BasePlatformTestCase() {
    private fun <T> withBridge(block: (KatachiDebugBridge) -> T): T {
        val previous = System.getProperty("katachi.debugBridge")
        System.setProperty("katachi.debugBridge", "true")
        try {
            return block(KatachiDebugBridge(project))
        } finally {
            if (previous == null) System.clearProperty("katachi.debugBridge") else System.setProperty("katachi.debugBridge", previous)
        }
    }

    private class Recording : AnAction() {
        val projects = mutableListOf<Project?>()

        override fun actionPerformed(e: AnActionEvent) {
            projects += e.project
        }
    }

    fun `test performAction はプロジェクトを文脈にしてアクションを実行する`() {
        val action = Recording()
        ActionManager.getInstance().registerAction(ACTION_ID, action)
        try {
            val answer = withBridge { it.performAction(ACTION_ID) }

            assertEquals("performed=$ACTION_ID", answer)
            assertEquals(listOf<Project?>(project), action.projects)
        } finally {
            ActionManager.getInstance().unregisterAction(ACTION_ID)
        }
    }

    fun `test performAction は無いアクションで performed=none を返す`() {
        assertEquals("performed=none", withBridge { it.performAction("katachi.test.NoSuchAction") })
    }

    fun `test performAction はデバッグブリッジが切れていると失敗する`() {
        Assert.assertThrows(IllegalStateException::class.java) { KatachiDebugBridge(project).performAction(ACTION_ID) }
    }

    private companion object {
        const val ACTION_ID = "katachi.test.DebugBridgePerformAction"
    }
}
