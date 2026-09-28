package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.FieldError
import me.tbsten.katachi.intellij.presentation.FieldSlot
import me.tbsten.katachi.intellij.presentation.FormState
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import java.nio.file.Path

/**
 * The generate dialog's state: plain values, so the Composable (D2) draws it and the tests read it
 * without an IDE. [GenerateDialogViewModel] keeps every member consistent with the others.
 */
internal data class GenerateDialogState(
    val request: GenerateDialogRequest,
    /** Every template of the selected definition (issue 0, issue 7), in list order; the select box shows even one. */
    val candidates: List<ModuleTemplate>,
    /** Still the old one while [candidates] is empty after a list change (then [canGenerate] is `false`). */
    val selectedTemplate: TemplateId,
    /** Set only when two or more definitions are involved (issue 7). */
    val selectedDefinition: ModuleId? = null,
    /** The definitions of the select box, in list order; empty with fewer than two (issue 7). */
    val definitions: List<KatachiModule> = emptyList(),
    /** The inputs, reusing the tool window's form: only [selectedTemplate] is in `selected`. */
    val form: FormState = FormState(),
    /** The selected template's fields in form order, with their values and errors. */
    val fields: List<GenerateDialogField> = emptyList(),
    /** The path the inputs produce, relative to the definition's project root, unfilled captures as `${name}`. */
    val targetPath: String = "",
    /** [targetPath] as a file, when nothing in it is left open. */
    val target: Path? = null,
    /** The existing-file notice (issue 6); `null` until checked, and again while the target changes. */
    val targetState: TargetState? = null,
    /** What changed in the template list while the dialog was open. */
    val listNotice: GenerateDialogListNotice? = null,
    val canGenerate: Boolean = false,
) {
    /** The selected template, or `null` when the list no longer has it. */
    val selected: ModuleTemplate? get() = candidates.firstOrNull { it.id == selectedTemplate }

    /** The notice under the target path (issue 6): the three kinds, or that katachi decides; `null` while unknown. */
    val targetNotice: TargetNotice?
        get() = when (targetState) {
            null -> null
            TargetState.Absent -> TargetNotice.WillCreate
            TargetState.Empty, TargetState.OwnProvisional -> TargetNotice.WillOverwriteEmpty
            TargetState.HasContent -> TargetNotice.CannotOverwrite
            TargetState.Undecided -> TargetNotice.DecidedByKatachi
        }
}

/** One line of the dialog's form. */
internal data class GenerateDialogField(
    /** Shown, or folded because its branch is not taken (then its value is kept but not sent). */
    val slot: FieldSlot,
    /** The raw text; `null` when never filled in (a required field shows no error yet). */
    val value: String?,
    /** The error to show; the `null` of a required field never filled in is on purpose. */
    val error: FieldError?,
    /** The value came from the place the dialog was opened from (issue 1): an initial value, still editable. */
    val seeded: Boolean,
) {
    val name: String get() = slot.parameter.name
}

/** The existing-file notice of issue 6. */
internal enum class TargetNotice {
    /** Nothing there: katachi creates it. */
    WillCreate,

    /** Empty (or still this plugin's provisional content): katachi overwrites it. */
    WillOverwriteEmpty,

    /** Has content: never overwritten, [Generate] cannot be pressed. */
    CannotOverwrite,

    /** A module-derived `<x>` keeps the file unknown: katachi checks when it runs (spike S1 §5). */
    DecidedByKatachi,
}

/** What happened to the template list while the dialog was open. */
internal sealed interface GenerateDialogListNotice {
    /** The selected template is gone; the first of the same definition is selected instead. */
    data class TemplateReplaced(val goneTitle: String) : GenerateDialogListNotice

    /** The selected definition has no template any more: [Generate] cannot be pressed. */
    data object NoCandidates : GenerateDialogListNotice
}

/** What the dialog's parts and the IDE ask of [GenerateDialogViewModel]. Dispatched on the EDT. */
internal sealed interface GenerateDialogIntent {
    data class SelectTemplate(val template: TemplateId) : GenerateDialogIntent

    data class SelectDefinition(val definition: ModuleId) : GenerateDialogIntent

    /** A capture or a parameter field, by name. */
    data class Input(val name: String, val value: String) : GenerateDialogIntent

    /** The shared template list changed (a reload): every template of every definition, in list order. */
    data class ListChanged(val templates: List<ModuleTemplate>) : GenerateDialogIntent

    /** Something outside the dialog wrote files (a VFS event): check the target again. */
    data object FilesChangedOutside : GenerateDialogIntent
}
