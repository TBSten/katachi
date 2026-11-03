package me.tbsten.katachi.intellij.ide.entry

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiConfigurable
import me.tbsten.katachi.intellij.ide.KatachiNotifications
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.KatachiToolWindowFactory
import me.tbsten.katachi.intellij.ide.dialog.GenerateDialogEnvironment
import me.tbsten.katachi.intellij.ide.dialog.GenerateDialogs
import me.tbsten.katachi.intellij.ide.dialog.realGenerateDialogEnvironmentOf
import me.tbsten.katachi.intellij.ide.SDK_CALL_LOG
import me.tbsten.katachi.intellij.ide.mustPropagate
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.entry.EntryEffects
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest

internal class EntryEffectsImpl(
    private val project: Project,
    private val notify: (String) -> Unit = { showBalloon(project, it) },
    private val showSettings: (Project) -> Unit = { ShowSettingsUtil.getInstance().showSettingsDialog(it, KatachiConfigurable::class.java) },
    private val environmentOf: (Project, GenerateDialogRequest) -> GenerateDialogEnvironment = ::realGenerateDialogEnvironmentOf,
    private val generate: (SingleFileGenerationRequest) -> Unit = { startGeneration(project, it, notify) },
    private val activateToolWindow: (Project) -> Unit = ::activateKatachiToolWindow,
) : EntryEffects {
    override fun openGenerateDialog(request: GenerateDialogRequest) {
        val answer = GenerateDialogs.open(project, request) { environmentOf(project, request) }
        answer.onFailure { notify(KatachiBundle.message("entry.openDialogFailed", it.message.orEmpty())) }
        answer.getOrNull()?.let(generate)
    }

    override fun openSettings() {
        sdkCall("open the katachi settings") { showSettings(project) }
            .onFailure { notify(KatachiBundle.message("entry.openSettingsFailed", it.message.orEmpty())) }
    }

    // Hand the highlight to the ViewModel before activating the tool window: a tool window the platform creates now must show the row highlighted.
    override fun revealTemplateInToolWindow(template: TemplateId) {
        sdkCall("show ${template.template} in the katachi tool window") {
            KatachiProjectService.getInstance(project).viewModel.dispatch(KatachiIntent.RevealTemplate(template))
            activateToolWindow(project)
        }.onFailure { notify(KatachiBundle.message("entry.revealFailed", it.message.orEmpty())) }
    }

    override fun revealInToolWindow(template: TemplateId, seeds: Map<String, String>) {
        sdkCall("show ${template.template} in the katachi tool window") {
            KatachiProjectService.getInstance(project).viewModel.dispatch(KatachiIntent.RevealAndSeed(template, seeds))
            activateToolWindow(project)
        }.onFailure { notify(KatachiBundle.message("entry.revealFailed", it.message.orEmpty())) }
    }
}

private fun activateKatachiToolWindow(project: Project) {
    ToolWindowManager.getInstance(project).getToolWindow(KatachiToolWindowFactory.TOOL_WINDOW_ID)?.activate(null, true)
}

private fun startGeneration(project: Project, request: SingleFileGenerationRequest, notify: (String) -> Unit) {
    val started = sdkCall("start generating ${request.template.template.template}") {
        KatachiProjectService.getInstance(project).generateFromEntry(request).invokeOnCompletion { failure ->
            if (failure == null || failure.mustPropagate) return@invokeOnCompletion
            SDK_CALL_LOG.warn("Could not generate ${request.template.template.template}", failure)
            notify(KatachiBundle.message("entry.generateFailed", failure.message.orEmpty()))
        }
    }
    started.onFailure { notify(KatachiBundle.message("entry.generateFailed", it.message.orEmpty())) }
}

private fun showBalloon(project: Project, text: String) {
    sdkCall("show the balloon \"$text\"") {
        NotificationGroupManager.getInstance().getNotificationGroup(KatachiNotifications.GROUP_ID)
            .createNotification(text, NotificationType.WARNING)
            .notify(project)
    }
}
