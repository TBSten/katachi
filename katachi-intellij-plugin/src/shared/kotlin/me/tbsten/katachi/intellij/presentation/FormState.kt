package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.TemplateId

/** The "existing files" combo of the footer, with the `onExisting` value it sends. */
internal enum class OnExistingChoice(val argValue: String) {
    Fail("fail"),
    Skip("skip"),
    Overwrite("overwrite"),
}

/** One input field: a parameter of a template row. */
internal data class FieldId(val templateId: TemplateId, val parameterName: String)

/**
 * The list + inline form, as the tool window holds it. Never persisted; inputs of an unchecked row
 * are kept so that checking it again brings them back.
 */
internal data class FormState(
    /** Checked rows, in the order they were checked. Generation orders them by the list instead. */
    val selected: List<TemplateId> = emptyList(),
    /** Rows whose inline form is open. A checked row can be folded and still be generated. */
    val expanded: Set<TemplateId> = emptySet(),
    /** Raw text per field. Booleans hold `true` / `false`; an absent or blank entry is "empty". */
    val inputs: Map<TemplateId, Map<String, String>> = emptyMap(),
    /** Fields whose link to same-named fields the user broke by typing a different value. */
    val unlinked: Set<FieldId> = emptySet(),
    /** The field whose typing currently drives each link group (see LinkedParameters.kt). */
    val linkSources: Map<LinkKey, FieldId> = emptyMap(),
    val onExisting: OnExistingChoice = OnExistingChoice.Fail,
    /** Rows whose expected-file list is open. Session only. */
    val fileListsOpen: Set<TemplateId> = emptySet(),
) {
    fun inputsOf(templateId: TemplateId): Map<String, String> = inputs[templateId].orEmpty()

    fun inputOf(field: FieldId): String? = inputs[field.templateId]?.get(field.parameterName)

    fun isSelected(templateId: TemplateId): Boolean = templateId in selected

    fun withInput(field: FieldId, value: String): FormState {
        val row = inputsOf(field.templateId) + (field.parameterName to value)
        return copy(inputs = inputs + (field.templateId to row))
    }
}
