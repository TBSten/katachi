package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.data.placement.conversionOf
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.presentation.expectedValueOf
import me.tbsten.katachi.intellij.presentation.replacePlaceholders
import me.tbsten.katachi.intellij.presentation.resolveExpectedPath
import java.nio.file.Path

internal data class DialogTarget(
    val relativePath: String,
    val absolute: Path?,
    val derivedUndecided: Boolean,
) {
    companion object {
        val NONE: DialogTarget = DialogTarget("", absolute = null, derivedUndecided = false)
    }
}

private val TOKEN = Regex("""\$\{([^}]*)}|<([^>/]*)>""")

private val CAPTURE_PLACEHOLDER = Regex("""\$\{([^}]*)}""")

internal fun displayTargetPathOf(relativePath: String): String = CAPTURE_PLACEHOLDER.replace(relativePath) { "<${it.groupValues[1]}>" }

private val DERIVED = Regex("""<([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)>""")

internal fun dialogTargetOf(
    detail: TemplateDetailModel,
    inputs: Map<String, String>,
    seeds: Map<String, String>,
    root: Path,
    origin: Path,
): DialogTarget {
    val file = detail.files.firstOrNull() ?: return DialogTarget.NONE
    val pattern = file.pattern
    val parameters = allParametersOf(detail).associateBy { it.name }
    // Below a module capture whose values pick an existing module, katachi said where the file lands
    // there: the module's real directory and every name built from the value, nothing left to guess.
    file.modulePlacement?.choiceFor(inputs)?.let { choice ->
        val relative = fillDerived(replacePlaceholders(choice.path) { name -> valueOf(name, inputs, parameters) }) { name -> valueOf(name, inputs, parameters) }
        val capturesLeft = relative.contains("\${")
        return DialogTarget(relative, absolute = if (capturesLeft) null else resolveExpectedPath(root, relative), derivedUndecided = false)
    }
    val originSegments = if (origin.startsWith(root)) root.relativize(origin).map { it.toString() } else emptyList()
    val segments = pattern.split('/').mapIndexed { index, segment ->
        val fromOrigin = originSegments.getOrNull(index)
        if (fromOrigin != null && readsDerived(segment) && namesOf(segment).all { name -> seeds[name]?.let { it == inputs[name] } == true }) {
            fromOrigin
        } else {
            fillDerived(replacePlaceholders(segment) { name -> valueOf(name, inputs, parameters) }) { name -> valueOf(name, inputs, parameters) }
        }
    }
    val relative = segments.joinToString("/")
    val capturesLeft = relative.contains("\${")
    val derivedLeft = TOKEN.findAll(relative).any { it.groupValues[2].isNotEmpty() }
    return DialogTarget(
        relativePath = relative,
        absolute = if (capturesLeft || derivedLeft) null else resolveExpectedPath(root, relative),
        derivedUndecided = derivedLeft && !capturesLeft,
    )
}

private fun readsDerived(segment: String): Boolean = TOKEN.findAll(segment).any { it.groupValues[2].isNotEmpty() }

private fun namesOf(segment: String): List<String> =
    TOKEN.findAll(segment).map { it.groupValues[1].ifEmpty { it.groupValues[2].substringBefore('.') } }.toList()

internal fun fillDerived(text: String, valueOf: (String) -> String?): String = DERIVED.replace(text) { match ->
    val source = valueOf(match.groupValues[1])
    val conversion = conversionOf(match.groupValues[2])
    if (source == null || conversion == null) match.value else conversion(source)
}

private fun valueOf(name: String, inputs: Map<String, String>, parameters: Map<String, ParameterModel>): String? {
    val parameter = parameters[name] ?: return inputs[name]?.takeIf { it.isNotBlank() }
    return expectedValueOf(parameter, inputs, parameters)
}
