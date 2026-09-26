package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ParameterModel

/** What is wrong with one field. The UI words it; the data stays typed so tests do not pin text. */
internal sealed interface FieldError {
    /** A required String / Int / enum is empty (E-13). */
    data object Required : FieldError

    /** Not a decimal Int in range (E-11). */
    data class NotAnInt(val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE) : FieldError

    /** Not one of the enum's entries (E-12). */
    data class NotAcceptedValue(val value: String, val acceptedValues: List<String>) : FieldError
}

// ASCII digits only: Kotlin's toIntOrNull also takes full-width digits, which katachi may not (E-11).
private val ASCII_INT = Regex("""[+-]?[0-9]+""")

/**
 * The error of [parameter] holding [input] (raw text, `null` when never typed), or `null` when it is
 * fine. An empty optional field is fine: katachi uses its default.
 */
internal fun validateField(parameter: ParameterModel, input: String?): FieldError? {
    val text = input?.trim().orEmpty()
    return when (parameter) {
        is ParameterModel.BooleanParam, is ParameterModel.UnknownParam -> null
        is ParameterModel.StringParam -> if (text.isEmpty() && parameter.default == null) FieldError.Required else null
        is ParameterModel.IntParam -> when {
            text.isEmpty() -> if (parameter.default == null) FieldError.Required else null
            normalizedInt(text) == null -> FieldError.NotAnInt()
            else -> null
        }
        is ParameterModel.EnumParam -> when {
            text.isEmpty() -> if (parameter.default == null) FieldError.Required else null
            text !in parameter.acceptedValues -> FieldError.NotAcceptedValue(text, parameter.acceptedValues)
            else -> null
        }
    }
}

/**
 * [text] as katachi should receive it: `-0` → `0`, `+7` / `007` → `7`, surrounding blanks dropped.
 * `null` when it is not a decimal Int.
 */
internal fun normalizedInt(text: String): String? {
    val trimmed = text.trim()
    if (!ASCII_INT.matches(trimmed)) return null
    return trimmed.toIntOrNull()?.toString()
}

/** The `--arg` value of a valid, non-empty [input]: Ints normalized, anything else as typed. */
internal fun argValueOf(parameter: ParameterModel, input: String): String = when (parameter) {
    is ParameterModel.IntParam -> normalizedInt(input) ?: input
    is ParameterModel.EnumParam, is ParameterModel.BooleanParam -> input.trim()
    else -> input
}
