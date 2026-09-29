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

/**
 * [EntryEffects] over the IntelliJ API. Every SDK call goes through `sdkCall`: a failure becomes a
 * balloon, never a failed click; control flow is thrown again.
 *
 * The seams ([notify], [showSettings], [environmentOf], [generate], [activateToolWindow]) default to the real IDE calls;
 * tests swap them, because a modal dialog and the settings window cannot be shown headless.
 *
 * ```kotlin
 * KatachiProjectService.getInstance(project).entryEffects.openSettings()
 * ```
 */
internal class EntryEffectsImpl(
    private val project: Project,
    private val notify: (String) -> Unit = { showBalloon(project, it) },
    private val showSettings: (Project) -> Unit = { ShowSettingsUtil.getInstance().showSettingsDialog(it, KatachiConfigurable::class.java) },
    private val environmentOf: (Project, GenerateDialogRequest) -> GenerateDialogEnvironment = ::realGenerateDialogEnvironmentOf,
    private val generate: (SingleFileGenerationRequest) -> Unit = { startGeneration(project, it, notify) },
    private val activateToolWindow: (Project) -> Unit = ::activateKatachiToolWindow,
) : EntryEffects {
    /**
     * Opens the dialog and, when the user pressed [Generate], starts the generation in the project's
     * scope (the dialog is closed by then and writes nothing itself). Cancelling writes nothing.
     */
    override fun openGenerateDialog(request: GenerateDialogRequest) {
        val answer = GenerateDialogs.open(project, request) { environmentOf(project, request) }
        answer.onFailure { notify(KatachiBundle.message("entry.openDialogFailed", it.message.orEmpty())) }
        answer.getOrNull()?.let(generate)
    }

    override fun openSettings() {
        sdkCall("open the katachi settings") { showSettings(project) }
            .onFailure { notify(KatachiBundle.message("entry.openSettingsFailed", it.message.orEmpty())) }
    }

    /**
     * Hands the highlight to the project service's ViewModel first, then brings the tool window up
     * as `GenerateFromTemplateAction` does. The state outlives the tool window's content, so a
     * tool window the platform creates now shows the row highlighted and scrolls to it (C2).
     */
    override fun revealTemplateInToolWindow(template: TemplateId) {
        sdkCall("show ${template.template} in the katachi tool window") {
            KatachiProjectService.getInstance(project).viewModel.dispatch(KatachiIntent.RevealTemplate(template))
            activateToolWindow(project)
        }.onFailure { notify(KatachiBundle.message("entry.revealFailed", it.message.orEmpty())) }
    }
}

/** Shows and focuses the `katachi` tool window, creating its content the first time. */
private fun activateKatachiToolWindow(project: Project) {
    ToolWindowManager.getInstance(project).getToolWindow(KatachiToolWindowFactory.TOOL_WINDOW_ID)?.activate(null, true)
}

/**
 * Runs [request] through the project service's one generation (`KatachiProjectService.generateFromEntry`),
 * the same one the dialog's existing-file check asks: a retry after a failure then knows the file still
 * holds its own provisional content (issue 16). Failures and refusals are told by the generation itself
 * (`IdeEffects`); only something it did not foresee is told here.
 */
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

/** A warning balloon in the `katachi` group, whether or not the tool window is shown. */
private fun showBalloon(project: Project, text: String) {
    sdkCall("show the balloon \"$text\"") {
        NotificationGroupManager.getInstance().getNotificationGroup(KatachiNotifications.GROUP_ID)
            .createNotification(text, NotificationType.WARNING)
            .notify(project)
    }
}
