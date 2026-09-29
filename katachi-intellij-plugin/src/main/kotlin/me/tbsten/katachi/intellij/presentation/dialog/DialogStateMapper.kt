package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.FieldError
import me.tbsten.katachi.intellij.presentation.FieldSlot
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.presentation.fieldSlotsOf
import me.tbsten.katachi.intellij.presentation.validateField
import java.nio.file.Path

/**
 * [core] as the dialog shows it. [checked] is the last answer of the existing-file check and the
 * file it was about: shown only while it is still the target.
 *
 * [Generate] can be pressed when the selected template is still listed, every shown field is valid
 * (a required one filled in) and the target is not a file with content (issue 6). A check still
 * running does not block: generating checks again before writing anything (E3).
 */
internal fun dialogStateOf(
    core: DialogCore,
    request: GenerateDialogRequest,
    rootOf: (KatachiModule) -> Path,
    checked: Pair<Path, TargetState>?,
): GenerateDialogState {
    val listed = core.all.firstOrNull { it.id == core.selectedId }
    val detail = listed?.template?.detail
    val inputs = core.inputs
    val fields = detail?.let { fieldSlotsOf(it, inputs) }.orEmpty().map { slot ->
        val value = inputs[slot.parameter.name]
        val error = if (slot is FieldSlot.Shown) validateField(slot.parameter, value) else null
        GenerateDialogField(
            slot = slot,
            value = value,
            // A field never filled in shows no "required" yet, as in the tool window.
            error = error?.takeUnless { it == FieldError.Required && value == null },
            seeded = slot.parameter.name in core.seeds,
        )
    }
    val valid = detail != null && fields.all { it.slot !is FieldSlot.Shown || validateField(it.slot.parameter, it.value) == null }
    val target = if (listed == null || detail == null) {
        DialogTarget.NONE
    } else {
        dialogTargetOf(detail, inputs, core.seeds, rootOf(listed.module), request.origin.path)
    }
    // A value katachi would refuse (a `/` in a capture) makes no file to check.
    val absolute = target.absolute.takeIf { valid }
    val targetState = when {
        absolute != null -> checked?.takeIf { it.first == absolute }?.second
        valid && target.derivedUndecided -> TargetState.Undecided
        else -> null
    }
    val definitions = core.all.map { it.module }.distinctBy { it.id }
    return GenerateDialogState(
        request = request,
        candidates = core.candidates,
        selectedTemplate = core.selectedId,
        selectedDefinition = core.definition.takeIf { definitions.size >= 2 },
        definitions = definitions.takeIf { it.size >= 2 }.orEmpty(),
        form = core.form,
        fields = fields,
        targetPath = target.relativePath,
        target = absolute,
        targetState = targetState,
        listNotice = core.listNotice,
        canGenerate = valid && targetState != TargetState.HasContent,
    )
}
