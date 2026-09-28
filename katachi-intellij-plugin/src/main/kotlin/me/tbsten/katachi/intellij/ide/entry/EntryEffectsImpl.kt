package me.tbsten.katachi.intellij.ide.entry

import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EntryEffects
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest

/**
 * [EntryEffects] over the IntelliJ API. Every SDK call goes through `sdkCall`: a failure becomes a
 * balloon, never a failed click; control flow is thrown again.
 *
 * ```kotlin
 * KatachiProjectService.getInstance(project).entryEffects.openSettings()
 * ```
 */
internal class EntryEffectsImpl(private val project: Project) : EntryEffects {
    // TODO(D4): open KatachiGenerateDialog and hand its request to SingleFileGeneration.
    override fun openGenerateDialog(request: GenerateDialogRequest) = Unit

    // TODO(D4): ShowSettingsUtil on the katachi page (issue 14).
    override fun openSettings() = Unit

    // TODO(C2): activate the tool window and dispatch KatachiIntent.RevealTemplate.
    override fun revealTemplateInToolWindow(template: TemplateId) = Unit
}
