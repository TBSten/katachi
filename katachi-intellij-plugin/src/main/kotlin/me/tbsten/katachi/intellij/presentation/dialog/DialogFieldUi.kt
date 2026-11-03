package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.FieldSlot
import me.tbsten.katachi.intellij.presentation.FieldUi
import me.tbsten.katachi.intellij.presentation.LinkUi
import me.tbsten.katachi.intellij.presentation.captureHintOf
import me.tbsten.katachi.intellij.presentation.expectedValueOf
import me.tbsten.katachi.intellij.presentation.fieldErrorText
import me.tbsten.katachi.intellij.presentation.suggestionsOf
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogStrings

internal fun dialogFieldsUiOf(state: GenerateDialogState, strings: GenerateDialogStrings): List<FieldUi> {
    val selected = state.selected ?: return emptyList()
    val detail = selected.template.detail ?: return emptyList()
    val inputs = state.form.inputsOf(selected.id)
    val parameters = allParametersOf(detail).associateBy { it.name }
    return state.fields.map { field ->
        val parameter = field.slot.parameter
        val id = FieldId(selected.id, parameter.name)
        val error = field.error?.let { fieldErrorText(it, strings) }
        val value = field.value
        if (field.slot is FieldSlot.Collapsed) {
            return@map FieldUi.Collapsed(id, strings.collapsedField(parameter.name, field.slot.controllerName, field.slot.whenValue))
        }
        when (parameter) {
            is ParameterModel.BooleanParam -> FieldUi.Bool(
                id = id,
                label = parameter.name,
                checked = (value?.trim()?.takeIf { it.isNotEmpty() } ?: parameter.default ?: "true") == "true",
                link = LinkUi.None,
            )
            is ParameterModel.EnumParam -> FieldUi.Choice(
                id = id,
                label = parameter.name,
                isRequired = parameter.isRequired,
                options = parameter.acceptedValues,
                selectedIndex = parameter.acceptedValues.indexOf(value?.trim()?.takeIf { it.isNotEmpty() } ?: parameter.default),
                placeholder = strings.chooseOne,
                error = error,
                link = LinkUi.None,
            )
            is ParameterModel.CaptureParam -> FieldUi.Text(
                id = id,
                label = parameter.name,
                isRequired = true,
                value = value.orEmpty(),
                placeholder = null,
                isNumber = false,
                error = error,
                link = LinkUi.None,
                isMultiline = null,
                multilineTooltip = null,
                hint = captureHintOf(parameter, strings::capturePathHint, strings::captureModuleHint),
                suggestions = suggestionsOf(parameter, state.captureCandidates[id], value, error, strings),
            )
            is ParameterModel.StringParam, is ParameterModel.IntParam, is ParameterModel.UnknownParam -> {
                val multiline = if (parameter is ParameterModel.StringParam) parameter.name in state.multiline else null
                FieldUi.Text(
                    id = id,
                    label = parameter.name,
                    isRequired = parameter.isRequired,
                    value = value.orEmpty(),
                    placeholder = parameter.default?.let { expectedValueOf(parameter, inputs - parameter.name, parameters) },
                    isNumber = parameter is ParameterModel.IntParam,
                    error = error,
                    link = LinkUi.None,
                    isMultiline = multiline,
                    multilineTooltip = multiline?.let { if (it) strings.multilineOff else strings.multilineOn },
                )
            }
        }
    }
}
