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

/**
 * Hosts the `katachi` tool window. Each feature is a tab of its own, so more can be added next to
 * the first one; for now there is one, "Template", which draws [KatachiProjectService]'s ViewModel
 * with the Composable shared with the headless preview. ⟳ / ■ and ⚙ sit in the title bar.
 *
 * [DumbAware]: nothing here reads PSI or indexes, so everything works while indexing (E-48).
 *
 * [JvmDefaultWithoutCompatibility]: without it the compiler writes a method into this class for each
 * default method of [ToolWindowFactory], each calling the interface's own. The Plugin Verifier then
 * reports deprecated (`isApplicable`, `isDoNotActivateOnStart`) and experimental (`getAnchor`,
 * `getIcon`, `manage`) API used by katachi, although katachi overrides none of them.
 */
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

/**
 * The tool window's texts in the IDE's language: the language of the `KatachiBundle` file the platform
 * resolves, so the tool window always speaks the language of the menus and notifications.
 */
internal fun toolWindowStrings(): KatachiStrings = KatachiStrings.of(KatachiBundle.message("toolWindow.language"))
