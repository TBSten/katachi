package me.tbsten.katachi.intellij.ide.entry

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.data.generate.EntryGenerationRefusal
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationResult
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiConfigurable
import me.tbsten.katachi.intellij.ide.KatachiNotifications
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.KatachiToolWindowFactory
import me.tbsten.katachi.intellij.ide.dialog.GenerateDialogEnvironment
import me.tbsten.katachi.intellij.ide.dialog.GenerateDialogs
import me.tbsten.katachi.intellij.ide.dialog.realGenerateDialogEnvironmentOf
import me.tbsten.katachi.intellij.ide.dialog.singleFileGenerationOf
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

/** Runs the generation of [request] in the project service's scope; a refusal that only the run can tell is a balloon. */
private fun startGeneration(project: Project, request: SingleFileGenerationRequest, notify: (String) -> Unit) {
    val started = sdkCall("start generating ${request.template.template.template}") {
        val service = KatachiProjectService.getInstance(project)
        val generation = singleFileGenerationOf(project)
        service.scope.launch {
            val result = sdkCall("generate ${request.template.template.template}") { generation.run(request) }
            // Failures are told by the generation itself (IdeEffects.notifyEntryGenerationFailed).
            result.onFailure { notify(KatachiBundle.message("entry.generateFailed", it.message.orEmpty())) }
            (result.getOrNull() as? SingleFileGenerationResult.Refused)?.let { notify(refusalText(it.refusal)) }
        }
    }
    started.onFailure { notify(KatachiBundle.message("entry.generateFailed", it.message.orEmpty())) }
}

private fun refusalText(refusal: EntryGenerationRefusal): String = when (refusal) {
    is EntryGenerationRefusal.TargetHasContent -> KatachiBundle.message("dialog.generateRefused", KatachiBundle.message("dialog.target.hasContent"))
    is EntryGenerationRefusal.ModuleMissing -> KatachiBundle.message("dialog.generateRefused", KatachiBundle.message("entry.moduleMissing", refusal.directory))
}

/** A warning balloon in the `katachi` group, whether or not the tool window is shown. */
private fun showBalloon(project: Project, text: String) {
    sdkCall("show the balloon \"$text\"") {
        NotificationGroupManager.getInstance().getNotificationGroup(KatachiNotifications.GROUP_ID)
            .createNotification(text, NotificationType.WARNING)
            .notify(project)
    }
}
