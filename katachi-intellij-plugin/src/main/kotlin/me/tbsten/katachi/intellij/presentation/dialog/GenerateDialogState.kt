package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.CandidateList
import me.tbsten.katachi.intellij.presentation.FieldError
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.FieldSlot
import me.tbsten.katachi.intellij.presentation.FormState
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import java.nio.file.Path

internal data class GenerateDialogState(
    val request: GenerateDialogRequest,
    val candidates: List<ModuleTemplate>,
    val selectedTemplate: TemplateId,
    val selectedDefinition: ModuleId? = null,
    val definitions: List<KatachiModule> = emptyList(),
    val form: FormState = FormState(),
    val fields: List<GenerateDialogField> = emptyList(),
    val targetPath: String = "",
    val target: Path? = null,
    val targetState: TargetState? = null,
    val listNotice: GenerateDialogListNotice? = null,
    val canGenerate: Boolean = false,
    val captureCandidates: Map<FieldId, CandidateList> = emptyMap(),
    val multiline: Set<String> = emptySet(),
) {
    val selected: ModuleTemplate? get() = candidates.firstOrNull { it.id == selectedTemplate }

    val targetNotice: TargetNotice?
        get() = when (targetState) {
            null -> null
            TargetState.Absent -> TargetNotice.WillCreate
            TargetState.Empty, TargetState.OwnProvisional -> TargetNotice.WillOverwriteEmpty
            TargetState.HasContent -> TargetNotice.CannotOverwrite
            TargetState.Undecided -> TargetNotice.DecidedByKatachi
        }
}

internal data class GenerateDialogField(
    val slot: FieldSlot,
    val value: String?,
    val error: FieldError?,
    val seeded: Boolean,
) {
    val name: String get() = slot.parameter.name
}

internal enum class TargetNotice {
    WillCreate,

    WillOverwriteEmpty,

    CannotOverwrite,

    DecidedByKatachi,
}

internal sealed interface GenerateDialogListNotice {
    data class TemplateReplaced(val goneTitle: String) : GenerateDialogListNotice

    data object NoCandidates : GenerateDialogListNotice
}

internal sealed interface GenerateDialogIntent {
    data class SelectTemplate(val template: TemplateId) : GenerateDialogIntent

    data class SelectDefinition(val definition: ModuleId) : GenerateDialogIntent

    data class Input(val name: String, val value: String) : GenerateDialogIntent

    data class ListChanged(val templates: List<ModuleTemplate>) : GenerateDialogIntent

    data class ToggleMultiline(val name: String) : GenerateDialogIntent

    data object FilesChangedOutside : GenerateDialogIntent
}
