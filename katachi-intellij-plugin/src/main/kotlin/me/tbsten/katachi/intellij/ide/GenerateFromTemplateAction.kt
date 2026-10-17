package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import me.tbsten.katachi.intellij.presentation.KatachiIntent

/**
 * "katachi: Generate from Template", reachable from Find Action (no shortcut, spec 04): opens the
 * tool window with the cursor in the search field (T-3). The request goes to the ViewModel first, so a tool window
 * the platform creates now, or a list still loading, takes the focus when it shows.
 */
internal class GenerateFromTemplateAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        sdkCall("open the katachi tool window") {
            KatachiProjectService.getInstance(project).viewModel.dispatch(KatachiIntent.FocusSearch)
            ToolWindowManager.getInstance(project).getToolWindow(KatachiToolWindowFactory.TOOL_WINDOW_ID)?.activate(null, true)
        }
    }
}
