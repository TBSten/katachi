package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogStrings
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogUiState
import me.tbsten.katachi.intellij.ui.dialog.ListNoticeUi
import me.tbsten.katachi.intellij.ui.dialog.TargetNoticeUi

internal fun dialogUiStateOf(state: GenerateDialogState, strings: GenerateDialogStrings, refusal: String? = null): GenerateDialogUiState {
    val manyRoots = state.definitions.map { it.linkedRootPath }.distinct().size > 1
    return GenerateDialogUiState(
        templateOptions = templateLabelsOf(state.candidates),
        selectedTemplate = state.candidates.indexOfFirst { it.id == state.selectedTemplate },
        definitionOptions = state.definitions.map { definitionLabelOf(it, manyRoots) },
        selectedDefinition = state.definitions.indexOfFirst { it.id == state.selectedDefinition }.coerceAtLeast(0),
        summary = state.selected?.template?.detail?.summary ?: state.selected?.template?.summary?.summary,
        fields = dialogFieldsUiOf(state, strings),
        targetPath = displayTargetPathOf(state.targetPath),
        targetNotice = when (state.targetNotice) {
            TargetNotice.WillCreate -> TargetNoticeUi.WillCreate
            TargetNotice.WillOverwriteEmpty -> TargetNoticeUi.WillOverwriteEmpty
            TargetNotice.CannotOverwrite -> TargetNoticeUi.CannotOverwrite
            // katachi decides when it runs; nothing to announce beforehand.
            TargetNotice.DecidedByKatachi, null -> null
        },
        listNotice = when (state.listNotice) {
            is GenerateDialogListNotice.TemplateReplaced -> ListNoticeUi.TemplateReplaced(state.selected?.template?.title.orEmpty())
            GenerateDialogListNotice.NoCandidates -> ListNoticeUi.NoCandidates
            null -> null
        },
        refusal = refusal,
        canGenerate = state.canGenerate,
    )
}

private fun definitionLabelOf(module: KatachiModule, withRoot: Boolean): String =
    if (withRoot) "${module.rootName} › ${module.gradlePath}" else module.gradlePath

private fun templateLabelsOf(candidates: List<ModuleTemplate>): List<String> {
    val base = candidates.map { "${it.template.roleName} › ${it.template.title}" }
    val withId = candidates.mapIndexed { i, c -> if (base.count { it == base[i] } > 1) "${base[i]} (${c.template.template})" else base[i] }
    return candidates.mapIndexed { i, c -> if (withId.count { it == withId[i] } > 1) "${withId[i]} — ${c.module.gradlePath}" else withId[i] }
}
