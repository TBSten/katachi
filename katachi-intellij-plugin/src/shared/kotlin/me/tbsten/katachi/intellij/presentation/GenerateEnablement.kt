package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId

/** Why the Generate button cannot be pressed; the footer shows the first one only. */
internal sealed interface GenerateBlocker {
    data object NothingSelected : GenerateBlocker

    /** A checked row cannot be generated at all (it became unavailable after a reload). */
    data class Unavailable(val templateId: TemplateId) : GenerateBlocker

    /** Pressing the reason opens [templateId]'s form and focuses [parameterName], even outside the search. */
    data class InvalidField(val templateId: TemplateId, val parameterName: String, val error: FieldError) : GenerateBlocker

    /** A checked row expects a file whose target is a wildcard (E-27). */
    data class UnresolvedPath(val templateId: TemplateId, val fileName: String) : GenerateBlocker

    data object Generating : GenerateBlocker

    /** The first load without a cache. A background refresh over a cache does not block. */
    data object InitialLoading : GenerateBlocker
}

/** Whether a generation or a first load without a cache is running: condition 4 of the spec. */
internal enum class BusyState { Idle, Generating, InitialLoading }

/**
 * The first reason the Generate button is disabled, or `null` when it can be pressed. Every checked
 * row counts, folded or outside the search (E-16).
 */
internal fun generateBlockerOf(rows: List<ModuleTemplate>, form: FormState, busy: BusyState): GenerateBlocker? {
    val checked = rows.filter { form.isSelected(it.id) }
    if (checked.isEmpty()) return GenerateBlocker.NothingSelected
    for (row in checked) {
        val detail = row.template.detail
        if (detail == null || !row.template.isAvailable) return GenerateBlocker.Unavailable(row.id)
        val inputs = form.inputsOf(row.id)
        for (parameter in shownParametersOf(detail, inputs)) {
            val error = validateField(parameter, inputs[parameter.name]) ?: continue
            return GenerateBlocker.InvalidField(row.id, parameter.name, error)
        }
    }
    for (row in checked) {
        val detail = row.template.detail ?: continue
        val unresolved = expectedFilesOf(detail, form.inputsOf(row.id)).firstOrNull { it.location is ExpectedLocation.Unresolved }
        if (unresolved != null) return GenerateBlocker.UnresolvedPath(row.id, unresolved.fileName)
    }
    return when (busy) {
        BusyState.Idle -> null
        BusyState.Generating -> GenerateBlocker.Generating
        BusyState.InitialLoading -> GenerateBlocker.InitialLoading
    }
}
