package me.tbsten.katachi.intellij.uitest.dialog

import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.presentation.FieldUi
import me.tbsten.katachi.intellij.presentation.dialog.dialogFieldsUiOf
import me.tbsten.katachi.intellij.ui.dialog.PropertiesGenerateDialogStrings
import me.tbsten.katachi.intellij.uitest.pbt.CandidateFieldView
import me.tbsten.katachi.intellij.uitest.pbt.CandidateReference
import me.tbsten.katachi.intellij.uitest.pbt.candidateFieldViolationsOf

/**
 * The directory candidates of the capture fields of the selected template, once the dialog's checks have answered: the same
 * invariants as the tool window's (`candidateViolationsOf`, ide-review-screens plan 4.2 invariants 1, 3 and 6) over the dialog's
 * fields. Nothing is judged after the dialog was cancelled (its candidates are no longer read).
 */
internal fun candidateProblemsOf(view: DialogView): List<String> {
    if (view.cancelled) return emptyList()
    val state = view.state
    val selected = state.selected ?: return emptyList()
    val detail = selected.template.detail ?: return emptyList()
    val captures = allParametersOf(detail).filterIsInstance<ParameterModel.CaptureParam>().associateBy { it.name }
    val entered = state.form.inputsOf(selected.id)
    return dialogFieldsUiOf(state, PropertiesGenerateDialogStrings.english()).filterIsInstance<FieldUi.Text>().flatMap { field ->
        val capture = captures[field.id.parameterName] ?: return@flatMap emptyList()
        val pattern = CandidateReference.patternHolding(detail.files.map { it.pattern }, capture.name) ?: return@flatMap emptyList()
        candidateFieldViolationsOf(CandidateFieldView(field, capture, pattern, entered, selected.module.linkedRootPath), view.candidateDisk)
    }
}
