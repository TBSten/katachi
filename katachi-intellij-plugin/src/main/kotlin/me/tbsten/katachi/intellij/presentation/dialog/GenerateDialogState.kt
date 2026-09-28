package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest

/**
 * The generate dialog's state. D1 adds the form (reusing `FormState`), the definition selection and
 * the validation; the members here are the ones other tasks already rely on.
 */
internal data class GenerateDialogState(
    val request: GenerateDialogRequest,
    /** Every template of the selected definition (issue 0, issue 7), in list order. */
    val candidates: List<ModuleTemplate>,
    val selectedTemplate: TemplateId,
    /** Set only when two or more definitions are involved (issue 7). */
    val selectedDefinition: ModuleId? = null,
    /** The path the inputs produce, with placeholders for what is not filled in. */
    val targetPath: String = "",
    /** The existing-file notice (issue 6); `null` until checked. */
    val targetState: TargetState? = null,
    val canGenerate: Boolean = false,
)

/** What the dialog's parts ask of [GenerateDialogViewModel]. D1 fixes the full set. */
internal sealed interface GenerateDialogIntent {
    data class SelectTemplate(val template: TemplateId) : GenerateDialogIntent

    data class SelectDefinition(val definition: ModuleId) : GenerateDialogIntent

    /** A capture or a parameter field, by name. */
    data class Input(val name: String, val value: String) : GenerateDialogIntent
}
