package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.allParametersOf

private val PLACEHOLDER = Regex("""\$\{([^}]*)}""")

/**
 * The value a parameter stands for in the "expected" preview: the input, else the default with its
 * own placeholders replaced one level deep, else `null` (the placeholder stays).
 *
 * Only plain substitution: `${name.lowercase()}` in a template previews as `${name}`, so the
 * expectation shows the value unprocessed (E-23). The screen always labels it "expected".
 */
internal fun expectedValueOf(parameter: ParameterModel, inputs: Map<String, String>, parameters: Map<String, ParameterModel>): String? {
    val input = inputs[parameter.name]
    if (!input.isNullOrBlank()) return input
    if (parameter is ParameterModel.BooleanParam) return parameter.default ?: "true"
    val default = parameter.default ?: return null
    return replacePlaceholders(default) { name ->
        val other = parameters[name] ?: return@replacePlaceholders null
        val otherInput = inputs[other.name]
        val otherDefault = other.default
        when {
            !otherInput.isNullOrBlank() -> otherInput
            // One level only: a default reading a default that reads a third is rare, and
            // recursing could loop on a cycle.
            otherDefault != null && !otherDefault.contains("\${") -> otherDefault
            else -> null
        }
    }
}

/** [text] with every `${name}` of [detail]'s parameters replaced by its [expectedValueOf], when known. */
internal fun expectedTextOf(text: String, detail: TemplateDetailModel, inputs: Map<String, String>): String {
    val parameters = allParametersOf(detail).associateBy { it.name }
    return replacePlaceholders(text) { name ->
        parameters[name]?.let { expectedValueOf(it, inputs, parameters) }
    }
}

/** Replaces each `${key}` of [text] by [valueOf] of its key, leaving it as it is when that is `null`. */
internal fun replacePlaceholders(text: String, valueOf: (String) -> String?): String =
    PLACEHOLDER.replace(text) { match -> valueOf(match.groupValues[1]) ?: match.value }
