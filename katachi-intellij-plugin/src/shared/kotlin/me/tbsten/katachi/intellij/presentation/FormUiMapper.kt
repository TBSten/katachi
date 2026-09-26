package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.allParametersOf

/** The inline form of a checked row: its fields in order, then the one-line file summary. */
internal fun formUiOf(row: ModuleTemplate, detail: TemplateDetailModel, state: KatachiScreenState, strings: KatachiStrings): FormUi {
    val inputs = state.form.inputsOf(row.id)
    return FormUi(
        summary = detail.summary ?: row.template.summary.summary,
        fields = fieldSlotsOf(detail, inputs).map { fieldUiOf(it, row, detail, state, strings) },
        files = fileSummaryUiOf(row, detail, state, strings),
    )
}

private fun fieldUiOf(
    slot: FieldSlot,
    row: ModuleTemplate,
    detail: TemplateDetailModel,
    state: KatachiScreenState,
    strings: KatachiStrings,
): FieldUi {
    val parameter = slot.parameter
    val id = FieldId(row.id, parameter.name)
    if (slot is FieldSlot.Collapsed) {
        return FieldUi.Collapsed(id, strings.collapsedField(parameter.name, slot.controllerName, slot.whenValue))
    }
    val inputs = state.form.inputsOf(row.id)
    val input = inputs[parameter.name]
    // A field never typed into shows no "required" error yet: every form would open all red.
    val error = validateField(parameter, input)
        ?.takeUnless { it == FieldError.Required && input == null }
        ?.let { fieldErrorText(it, strings) }
    val link = when {
        isUnlinked(id, state.form) -> LinkUi.Unlinked(KatachiIntent.Relink(id))
        isLinked(id, state.rows, state.form) -> LinkUi.Linked
        else -> LinkUi.None
    }
    val note = branchNoteOf(parameter, detail, inputs, strings)
    val parameters = allParametersOf(detail).associateBy { it.name }
    return when (parameter) {
        is ParameterModel.BooleanParam -> FieldUi.Bool(
            id = id,
            label = parameter.name,
            checked = (input?.trim()?.takeIf { it.isNotEmpty() } ?: parameter.default ?: "true") == "true",
            link = link,
            note = note,
        )
        is ParameterModel.EnumParam -> FieldUi.Choice(
            id = id,
            label = parameter.name,
            isRequired = parameter.isRequired,
            options = parameter.acceptedValues,
            selectedIndex = parameter.acceptedValues.indexOf(input?.trim()?.takeIf { it.isNotEmpty() } ?: parameter.default),
            placeholder = strings.chooseOne,
            error = error,
            link = link,
            note = note,
        )
        is ParameterModel.StringParam, is ParameterModel.IntParam, is ParameterModel.UnknownParam -> FieldUi.Text(
            id = id,
            label = parameter.name,
            isRequired = parameter.isRequired,
            value = input.orEmpty(),
            placeholder = parameter.default?.let { expectedValueOf(parameter, inputs - parameter.name, parameters) },
            isNumber = parameter is ParameterModel.IntParam,
            error = error,
            link = link,
            note = note,
            isMultiline = if (parameter is ParameterModel.StringParam) id in state.view.multilineFields else null,
        )
    }
}

internal fun fieldErrorText(error: FieldError, strings: KatachiStrings): String = when (error) {
    FieldError.Required -> strings.requiredError
    is FieldError.NotAnInt -> strings.notAnInt(error.min, error.max)
    is FieldError.NotAcceptedValue -> strings.notAccepted
}

/**
 * "off: UserRepositoryImpl.kt is not made" next to a parameter that has branches. A branch the
 * inputs take is described backwards: what going back to the preview value would do.
 */
private fun branchNoteOf(
    parameter: ParameterModel,
    detail: TemplateDetailModel,
    inputs: Map<String, String>,
    strings: KatachiStrings,
): String? {
    val branches = detail.branches.filter { it.parameterName == parameter.name }
    if (branches.isEmpty()) return null
    val current = branchValueOf(parameter, inputs)
    fun names(files: List<String>) = files.map { expectedTextOf(it, detail, inputs) }
    val parts = branches.flatMap { branch ->
        val taken = branch.value == current
        val value = if (taken) parameter.previewValue else branch.value
        val adds = if (taken) branch.removedFiles else branch.addedFiles
        val removes = if (taken) branch.addedFiles else branch.removedFiles
        listOfNotNull(
            adds.takeIf { it.isNotEmpty() }?.let { strings.branchAdds(value, names(it)) },
            removes.takeIf { it.isNotEmpty() }?.let { strings.branchRemoves(value, names(it)) },
        )
    }.distinct()
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ")
}

private fun fileSummaryUiOf(row: ModuleTemplate, detail: TemplateDetailModel, state: KatachiScreenState, strings: KatachiStrings): FileSummaryUi {
    val files = expectedFilesOf(detail, state.form.inputsOf(row.id))
    val summary = fileSummaryOf(files)
    val first = summary.firstFileName
    return FileSummaryUi(
        text = when {
            first == null -> strings.generatesNothing
            summary.otherCount == 0 -> strings.generatesOne(first)
            else -> strings.generatesMany(first, summary.otherCount)
        },
        hasWarning = summary.hasUnresolved,
        isOpen = row.id in state.form.fileListsOpen,
        caption = strings.expectedCaption,
        files = files.map { expectedFileUiOf(it, state.view, strings) },
        toggle = KatachiIntent.ToggleFileList(row.id),
    )
}

internal fun expectedFileUiOf(file: ExpectedFile, view: ViewState, strings: KatachiStrings): ExpectedFileUi =
    when (val location = file.location) {
        is ExpectedLocation.Known -> ExpectedFileUi(
            name = file.fileName,
            location = abbreviateDirectory(file.directory.orEmpty()),
            locationTooltip = location.path,
            badge = strings.alreadyExists.takeIf { location.path in view.existingPaths },
            isWarning = false,
        )
        is ExpectedLocation.Unresolved -> ExpectedFileUi(
            name = file.fileName,
            location = location.patterns.joinToString(),
            locationTooltip = null,
            badge = null,
            isWarning = true,
        )
        ExpectedLocation.FromBranch -> ExpectedFileUi(
            name = file.fileName,
            location = strings.targetFromBranch,
            locationTooltip = null,
            badge = null,
            isWarning = false,
        )
    }
