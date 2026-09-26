package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.allParametersOf

/** The form carried over to a reloaded list, and the checked templates that are gone. */
internal data class ReloadMergeResult(
    val form: FormState,
    /** Checked before, missing (or no longer checkable) now: "Repository is gone" for 3 seconds. */
    val removedTemplates: List<TemplateId>,
)

/**
 * Keeps checks, open forms and inputs as long as the template id and the parameter name still
 * exist (E-45). A template that disappeared, or can no longer be checked, is unchecked; its inputs
 * and the inputs of vanished parameters are dropped.
 */
internal fun mergeAfterReload(form: FormState, newRows: List<ModuleTemplate>): ReloadMergeResult {
    val available = newRows.filter { it.template.isAvailable }.associateBy { it.id }
    val parameterNames: Map<TemplateId, Set<String>> = newRows.associate { row ->
        row.id to row.template.detail?.let { detail -> allParametersOf(detail).map { it.name }.toSet() }.orEmpty()
    }
    val removed = form.selected.filter { it !in available }
    fun keeps(field: FieldId): Boolean = field.parameterName in parameterNames[field.templateId].orEmpty()

    val merged = form.copy(
        selected = form.selected.filter { it in available },
        expanded = form.expanded.filterTo(LinkedHashSet()) { it in available },
        inputs = form.inputs
            .filterKeys { it in parameterNames }
            .mapValues { (id, row) -> row.filterKeys { keeps(FieldId(id, it)) } }
            .filterValues { it.isNotEmpty() },
        unlinked = form.unlinked.filterTo(LinkedHashSet(), ::keeps),
        linkSources = form.linkSources.filterValues(::keeps),
        fileListsOpen = form.fileListsOpen.filterTo(LinkedHashSet()) { it in available },
    )
    return ReloadMergeResult(merged, removed)
}
