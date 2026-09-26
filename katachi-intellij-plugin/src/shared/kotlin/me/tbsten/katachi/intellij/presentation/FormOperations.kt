package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.allParametersOf

/**
 * Checks or unchecks [templateId]. Checking opens its form and joins same-named fields (E-14);
 * unchecking folds it and keeps its inputs for the next check. An unavailable row does not check.
 */
internal fun toggleCheck(form: FormState, rows: List<ModuleTemplate>, templateId: TemplateId): FormState {
    if (form.isSelected(templateId)) {
        return form.copy(selected = form.selected - templateId, expanded = form.expanded - templateId)
    }
    val row = rows.firstOrNull { it.id == templateId } ?: return form
    if (!row.template.isAvailable) return form
    val checked = form.copy(selected = form.selected + templateId, expanded = form.expanded + templateId)
    return joinLinks(checked, rows, templateId)
}

/** Folds or opens a row's form without touching the check. */
internal fun setExpanded(form: FormState, templateId: TemplateId, expanded: Boolean): FormState =
    form.copy(expanded = if (expanded) form.expanded + templateId else form.expanded - templateId)

internal fun toggleFileList(form: FormState, templateId: TemplateId): FormState =
    form.copy(
        fileListsOpen = if (templateId in form.fileListsOpen) form.fileListsOpen - templateId else form.fileListsOpen + templateId,
    )

/** "Uncheck all" after a result: back to the start, inputs dropped, the existing-files choice kept. */
internal fun uncheckAll(form: FormState): FormState = FormState(onExisting = form.onExisting)

/**
 * "Retry the rest" (E-38): rows that were written or skipped are unchecked; failed, stopped,
 * interrupted and not-run rows stay checked with their inputs, their forms open.
 */
internal fun retryRemaining(form: FormState, report: GenerationReport): FormState {
    val keep = report.retryTargets.toSet()
    val done = report.items.map { it.templateId }.filter { it !in keep }.toSet()
    return form.copy(
        selected = form.selected.filter { it !in done },
        expanded = form.expanded - done + keep,
    )
}

/**
 * "Generate more": the same rows stay checked and open, String fields emptied so a new name can go
 * in; every other value stays.
 */
internal fun continueGenerating(form: FormState, rows: List<ModuleTemplate>): FormState {
    val stringFields = rows.filter { form.isSelected(it.id) }.associate { row ->
        row.id to row.template.detail?.let { detail ->
            allParametersOf(detail).filterIsInstance<ParameterModel.StringParam>().map { it.name }.toSet()
        }.orEmpty()
    }
    val inputs = form.inputs.mapValues { (id, row) ->
        val names = stringFields[id] ?: return@mapValues row
        row.filterKeys { it !in names }
    }
    return form.copy(inputs = inputs, expanded = form.expanded + form.selected, linkSources = emptyMap())
}
