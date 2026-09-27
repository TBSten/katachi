package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.allParametersOf

/** The inline form of a checked row: its summary, then its fields in order. */
internal fun formUiOf(row: ModuleTemplate, detail: TemplateDetailModel, state: KatachiScreenState, strings: KatachiStrings): FormUi {
    val inputs = state.form.inputsOf(row.id)
    return FormUi(
        summary = detail.summary ?: row.template.summary.summary,
        fields = fieldSlotsOf(detail, inputs).map { fieldUiOf(it, row, detail, state, strings) },
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
    val parameters = allParametersOf(detail).associateBy { it.name }
    return when (parameter) {
        is ParameterModel.BooleanParam -> FieldUi.Bool(
            id = id,
            label = parameter.name,
            checked = (input?.trim()?.takeIf { it.isNotEmpty() } ?: parameter.default ?: "true") == "true",
            link = link,
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
        )
        is ParameterModel.StringParam, is ParameterModel.IntParam, is ParameterModel.UnknownParam -> {
            val multiline = if (parameter is ParameterModel.StringParam) id in state.view.multilineFields else null
            FieldUi.Text(
                id = id,
                label = parameter.name,
                isRequired = parameter.isRequired,
                value = input.orEmpty(),
                placeholder = parameter.default?.let { expectedValueOf(parameter, inputs - parameter.name, parameters) },
                isNumber = parameter is ParameterModel.IntParam,
                error = error,
                link = link,
                isMultiline = multiline,
                multilineTooltip = multiline?.let { if (it) strings.multilineOff else strings.multilineOn },
            )
        }
    }
}

internal fun fieldErrorText(error: FieldError, strings: KatachiStrings): String = when (error) {
    FieldError.Required -> strings.requiredError
    is FieldError.NotAnInt -> strings.notAnInt(error.min, error.max)
    is FieldError.NotAcceptedValue -> strings.notAccepted
}
