package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.CapturePlace
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
        // The same field as a String parameter's, with a note on where the value goes; never
        // multi-line, as a value is one directory level.
        is ParameterModel.CaptureParam -> FieldUi.Text(
            id = id,
            label = parameter.name,
            isRequired = true,
            value = input.orEmpty(),
            placeholder = null,
            isNumber = false,
            error = error,
            link = link,
            isMultiline = null,
            multilineTooltip = null,
            hint = captureHintOf(parameter, strings),
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
    is FieldError.InvalidCapture -> when (error.problem) {
        CaptureProblem.Separator -> strings.captureSeparatorError
        CaptureProblem.Dot -> strings.captureDotError
    }
}

/** Every place [capture] sits, its `*` written `<name>`; a module capture says it names a module. */
internal fun captureHintOf(capture: ParameterModel.CaptureParam, strings: KatachiStrings): String? =
    capture.places.map { place ->
        val marked = markedPatternOf(capture.name, place)
        if (place.isModule) strings.captureModuleHint(capture.name, marked) else strings.capturePathHint(capture.name, marked)
    }.distinct().joinToString(" / ").ifEmpty { null }

/**
 * [place]'s pattern with the capture's own `*` replaced by `<name>`: the `position`-th `/` level of
 * a file pattern, or the `position`-th `*` of a module key. A position out of range leaves it as it is.
 *
 * A file level can hold more than one capture (`"${capture("a")}-${capture("b")}"`), so [place.pattern]'s
 * generic `*`s at that level cannot tell them apart -- replacing every `*` there would mark both as the
 * same name. [place.segment] already names every capture on that one level (`${a}-${b}`), so the path
 * branch marks only this capture's own placeholder in it and leaves the others as their own name.
 */
internal fun markedPatternOf(name: String, place: CapturePlace): String {
    val mark = "<$name>"
    if (place.isModule) {
        val parts = place.pattern.split('*')
        if (place.position !in 0 until parts.size - 1) return place.pattern
        return parts.mapIndexed { index, part -> if (index == 0) part else (if (index - 1 == place.position) mark else "*") + part }.joinToString("")
    }
    val levels = place.pattern.split('/')
    if (place.position !in levels.indices) return place.pattern
    val markedLevel = place.segment.replace("\${$name}", mark)
    return levels.mapIndexed { index, level -> if (index == place.position) markedLevel else level }.joinToString("/")
}
