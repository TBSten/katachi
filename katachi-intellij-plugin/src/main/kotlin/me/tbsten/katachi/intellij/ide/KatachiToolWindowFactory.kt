package me.tbsten.katachi.intellij.ide

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiStrings
import me.tbsten.katachi.intellij.presentation.uiStateOf
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import org.jetbrains.jewel.bridge.addComposeTab
import java.time.Instant

// `@JvmDefaultWithoutCompatibility` keeps the compiler from writing bridge methods that make Plugin Verifier report deprecated/experimental API.
@JvmDefaultWithoutCompatibility
internal class KatachiToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val viewModel = sdkCall("create the katachi project service") { KatachiProjectService.getInstance(project).viewModel }.getOrNull() ?: return
        // Read once: the IDE changes its language with a restart.
        val strings = toolWindowStrings()
        sdkCall("add the Template tab") {
            toolWindow.addComposeTab(KatachiBundle.message("toolWindow.tab.template")) {
                val state by viewModel.state.collectAsState()
                KatachiToolWindowContent(uiStateOf(state, strings, Instant.now()), onIntent = viewModel::dispatch)
            }
        }
        // ⟳ / ■ stay in the title bar: the Template tab is the only tab, so they do not show over a tab they do nothing for.
        //  Move them into the tab's own toolbar if a second tab is ever added.
        sdkCall("add the title actions") { toolWindow.setTitleActions(listOf(ReloadOrStopAction(), OpenSettingsAction())) }
        // The platform creates the content the first time the tool window is shown: that is when
        // detection and the first load run (spec 04), never on opening the project.
        viewModel.dispatch(KatachiIntent.Opened)
    }

    companion object {
        // Must match the `id` of `<toolWindow>` in plugin.xml.
        const val TOOL_WINDOW_ID: String = "katachi"
    }
}

internal fun toolWindowStrings(): KatachiStrings = KatachiStrings.of(KatachiBundle.message("toolWindow.language"))
