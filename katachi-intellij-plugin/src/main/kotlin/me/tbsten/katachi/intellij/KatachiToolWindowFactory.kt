package me.tbsten.katachi.intellij

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import me.tbsten.katachi.intellij.ui.KatachiToolWindowState
import org.jetbrains.jewel.bridge.addComposeTab

/**
 * Hosts the `katachi` tool window's Compose (Jewel) UI.
 *
 * The Composable lives in `src/shared` so that the headless preview renders the same code.
 */
internal class KatachiToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.addComposeTab {
            // TODO: replace with the state machine of the screen spec (initialising, loading, list + form, ...).
            KatachiToolWindowContent(KatachiToolWindowState.Placeholder)
        }
    }

    companion object {
        /** Must match the `id` of `<toolWindow>` in plugin.xml. */
        const val TOOL_WINDOW_ID: String = "katachi"
    }
}
