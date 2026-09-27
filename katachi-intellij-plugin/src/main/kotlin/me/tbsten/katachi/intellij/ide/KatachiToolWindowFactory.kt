package me.tbsten.katachi.intellij.ide

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.uiStateOf
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import org.jetbrains.jewel.bridge.addComposeTab
import java.time.Instant

/**
 * Hosts the `katachi` tool window. Each feature is a tab of its own, so more can be added next to
 * the first one; for now there is one, "Template", which draws [KatachiProjectService]'s ViewModel
 * with the Composable shared with the headless preview. ⟳ / ■ and ⚙ sit in the title bar.
 *
 * [DumbAware]: nothing here reads PSI or indexes, so everything works while indexing (E-48).
 */
internal class KatachiToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val viewModel = sdkCall("create the katachi project service") { KatachiProjectService.getInstance(project).viewModel }.getOrNull() ?: return
        sdkCall("add the Template tab") {
            toolWindow.addComposeTab(KatachiBundle.message("toolWindow.tab.template")) {
                val state by viewModel.state.collectAsState()
                KatachiToolWindowContent(uiStateOf(state, JapaneseKatachiStrings, Instant.now()), onIntent = viewModel::dispatch)
            }
        }
        // TODO: ⟳ / ■ belong to the Template tab. Move them into that tab's own toolbar once a second
        //  tab exists, so they do not show over a tab they do nothing for.
        sdkCall("add the title actions") { toolWindow.setTitleActions(listOf(ReloadOrStopAction(), OpenSettingsAction())) }
        // The platform creates the content the first time the tool window is shown: that is when
        // detection and the first load run (spec 04), never on opening the project.
        viewModel.dispatch(KatachiIntent.Opened)
    }

    companion object {
        /** Must match the `id` of `<toolWindow>` in plugin.xml. */
        const val TOOL_WINDOW_ID: String = "katachi"
    }
}
