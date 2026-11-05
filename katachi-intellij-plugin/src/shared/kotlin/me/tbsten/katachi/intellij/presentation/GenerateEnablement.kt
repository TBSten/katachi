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

    /**
     * A checked row expects a file whose target is a wildcard (E-27), or one below a module capture
     * from a katachi that does not say where each module puts it. A module capture katachi does
     * describe is an [InvalidField] of its own field instead: its value is what is missing.
     */
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
    if (rows.none { form.isSelected(it.id) }) return GenerateBlocker.NothingSelected
    inputBlockersOf(rows, form).firstOrNull()?.let { return it }
    return when (busy) {
        BusyState.Idle -> null
        BusyState.Generating -> GenerateBlocker.Generating
        BusyState.InitialLoading -> GenerateBlocker.InitialLoading
    }
}

/**
 * Every reason the checked rows of [rows] cannot be generated with what [form] holds, in the order the footer would show
 * them one by one: the fields of each row (an unavailable row stops its own fields), then the files whose place is unknown.
 */
internal fun inputBlockersOf(rows: List<ModuleTemplate>, form: FormState): List<GenerateBlocker> = buildList {
    val checked = rows.filter { form.isSelected(it.id) }
    for (row in checked) {
        val detail = row.template.detail
        if (detail == null || !row.template.isAvailable) {
            add(GenerateBlocker.Unavailable(row.id))
            continue
        }
        val inputs = form.inputsOf(row.id)
        for (parameter in shownParametersOf(detail, inputs)) {
            val error = fieldErrorOf(row, detail, parameter, inputs[parameter.name]) ?: continue
            add(GenerateBlocker.InvalidField(row.id, parameter.name, error))
        }
    }
    for (row in checked) {
        val detail = row.template.detail ?: continue
        if (!row.template.isAvailable) continue
        for (file in expectedFilesOf(detail, form.inputsOf(row.id))) {
            locationBlockerOf(row.id, file)?.let(::add)
        }
    }
}

/**
 * What stops [file] of [templateId] from being generated, as far as its location tells, or `null`.
 * A module capture's value that is missing or names no module is worded as its field's error --
 * "enter feature", "choose an existing module" -- rather than as a target nobody can decide: the
 * field check above has usually said so already, and this only covers what it cannot see (values
 * that each name a module but together name none).
 */
private fun locationBlockerOf(templateId: TemplateId, file: ExpectedFile): GenerateBlocker? = when (val location = file.location) {
    is ExpectedLocation.Unresolved -> GenerateBlocker.UnresolvedPath(templateId, file.fileName)
    is ExpectedLocation.AwaitingModule -> GenerateBlocker.InvalidField(templateId, location.captureNames.first(), FieldError.Required)
    is ExpectedLocation.NoSuchModule -> GenerateBlocker.InvalidField(
        templateId,
        location.captureNames.first(),
        FieldError.NotAnExistingModule(location.existing.map { it.joinToString(":") }),
    )
    is ExpectedLocation.Known, ExpectedLocation.FromBranch -> null
}
