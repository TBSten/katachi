package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogStrings
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogUiState
import me.tbsten.katachi.intellij.ui.dialog.ListNoticeUi
import me.tbsten.katachi.intellij.ui.dialog.TargetNoticeUi

/**
 * [state] as the Composable draws it: the two select boxes, the form, the target path with its
 * notice, and the button. Worded from [strings] (the field errors and hints).
 *
 * A definition reads as its Gradle path, prefixed with the linked root's name when the definitions
 * come from more than one linked root, as the tool window's module headers do.
 *
 * A template reads as `role › title`; where that still repeats (the same role and title in two
 * modules, or two ids under one title), the template id and then the module are added, so no two
 * entries of the select box read alike.
 *
 * `refusal` (E3's pre-check saying no) is not part of [GenerateDialogState]; the frame that owns
 * the generation passes it in.
 *
 * ```kotlin
 * val ui = dialogUiStateOf(viewModel.state.value, BundleGenerateDialogStrings)
 * ```
 */
internal fun dialogUiStateOf(state: GenerateDialogState, strings: GenerateDialogStrings, refusal: String? = null): GenerateDialogUiState {
    val manyRoots = state.definitions.map { it.linkedRootPath }.distinct().size > 1
    return GenerateDialogUiState(
        templateOptions = templateLabelsOf(state.candidates),
        selectedTemplate = state.candidates.indexOfFirst { it.id == state.selectedTemplate },
        definitionOptions = state.definitions.map { definitionLabelOf(it, manyRoots) },
        selectedDefinition = state.definitions.indexOfFirst { it.id == state.selectedDefinition }.coerceAtLeast(0),
        summary = state.selected?.template?.detail?.summary ?: state.selected?.template?.summary?.summary,
        fields = dialogFieldsUiOf(state, strings),
        targetPath = state.targetPath,
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

/** One label per candidate, `role › title`, made unique by the id and then the module where it repeats. */
private fun templateLabelsOf(candidates: List<ModuleTemplate>): List<String> {
    val base = candidates.map { "${it.template.roleName} › ${it.template.title}" }
    val withId = candidates.mapIndexed { i, c -> if (base.count { it == base[i] } > 1) "${base[i]} (${c.template.template})" else base[i] }
    return candidates.mapIndexed { i, c -> if (withId.count { it == withId[i] } > 1) "${withId[i]} — ${c.module.gradlePath}" else withId[i] }
}
