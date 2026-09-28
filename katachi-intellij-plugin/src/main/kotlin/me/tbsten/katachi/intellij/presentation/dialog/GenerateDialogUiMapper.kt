package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogUiState

/**
 * The part of [GenerateDialogUiState] the frame has: the two select boxes, the target path and the
 * button. D2 adds the form, the notices and the error texts.
 *
 * A definition reads as its Gradle path, prefixed with the linked root's name when the definitions
 * come from more than one linked root, as the tool window's module headers do.
 */
internal fun dialogUiStateOf(state: GenerateDialogState): GenerateDialogUiState {
    val manyRoots = state.definitions.map { it.linkedRootPath }.distinct().size > 1
    return GenerateDialogUiState(
        templateOptions = state.candidates.map { it.template.title },
        selectedTemplate = state.candidates.indexOfFirst { it.id == state.selectedTemplate },
        definitionOptions = state.definitions.map { definitionLabelOf(it, manyRoots) },
        selectedDefinition = state.definitions.indexOfFirst { it.id == state.selectedDefinition }.coerceAtLeast(0),
        targetPath = state.targetPath,
        canGenerate = state.canGenerate,
    )
}

private fun definitionLabelOf(module: KatachiModule, withRoot: Boolean): String =
    if (withRoot) "${module.rootName} › ${module.gradlePath}" else module.gradlePath
