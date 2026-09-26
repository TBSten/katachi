package me.tbsten.katachi.intellij.ide

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.TitleAction
import me.tbsten.katachi.intellij.presentation.titleActionOf

/** ⟳ in the title bar, ■ while loading (spec 04 "再読み込み"). Disabled while generating (E-41). */
internal class ReloadOrStopAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val viewModel = e.project?.serviceIfCreated<KatachiProjectService>()?.viewModel
        if (viewModel == null) {
            e.presentation.isEnabled = false
            return
        }
        val state = viewModel.state.value
        val stop = titleActionOf(state) == TitleAction.Stop
        e.presentation.icon = if (stop) AllIcons.Actions.Suspend else AllIcons.Actions.Refresh
        e.presentation.text = KatachiBundle.message(if (stop) "action.stop.text" else "action.reload.text")
        e.presentation.description = KatachiBundle.message(if (stop) "action.stop.description" else "action.reload.description")
        e.presentation.isEnabled = stop || state.generation !is GenerationState.Running
    }

    override fun actionPerformed(e: AnActionEvent) {
        val viewModel = e.project?.serviceIfCreated<KatachiProjectService>()?.viewModel ?: return
        val loading = viewModel.state.value.loading != null
        viewModel.dispatch(if (loading) KatachiIntent.CancelLoad else KatachiIntent.Reload)
    }
}

/** ⚙ in the title bar: Settings | Tools | katachi. */
internal class OpenSettingsAction : DumbAwareAction(KatachiBundle.message("action.settings.text"), null, AllIcons.General.Settings) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, KatachiConfigurable::class.java)
    }
}
