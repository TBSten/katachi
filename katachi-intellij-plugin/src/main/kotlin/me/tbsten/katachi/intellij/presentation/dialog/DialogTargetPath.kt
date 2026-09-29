package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.presentation.expectedValueOf
import me.tbsten.katachi.intellij.presentation.replacePlaceholders
import me.tbsten.katachi.intellij.presentation.resolveExpectedPath
import java.nio.file.Path

/** The file the dialog's inputs produce, as far as the IDE can tell before katachi runs. */
internal data class DialogTarget(
    /** Relative to the definition's project root, `/` separated; a capture not filled in stays `${name}`, a derived part `<name>`. */
    val relativePath: String,
    /** [relativePath] under the root, when nothing in it is left open; `null` otherwise. */
    val absolute: Path?,
    /** Every capture is filled but a module-derived `<x>` remains: only katachi knows the file (spike S1 §5). */
    val derivedUndecided: Boolean,
) {
    companion object {
        val NONE: DialogTarget = DialogTarget("", absolute = null, derivedUndecided = false)
    }
}

private val TOKEN = Regex("""\$\{([^}]*)}|<([^>/]*)>""")

/**
 * [detail]'s file pattern with [inputs] put in (the sample path of the dialog, issue 1 and spike S1 §4).
 *
 * Below a module capture the JSON's `modulePlacements` describes, the module the inputs pick gives
 * the whole path instead (its directory as katachi's `ModuleResolver` answers it, the names built
 * from the value as katachi spells them). While they pick none, the pattern is used as below.
 *
 * A module-derived `<x>` has no value the JSON tells, so a segment holding one is taken from
 * [origin] (the origin's path relative to [root]) as long as every name that segment reads is still
 * the value [seeds] gave it: the origin is the one place whose spelling of `<x>` is known. A
 * notification's file therefore comes out as itself when nothing was rewritten (decision 21).
 * Anywhere else a `<x>` stays and the target is left to katachi.
 *
 * ```kotlin
 * dialogTargetOf(detail, mapOf("feature" to "home", "name" to "Home"), seeds, root, root.resolve("feature/home/ui"))
 * ```
 */
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
        val relative = replacePlaceholders(choice.path) { name -> valueOf(name, inputs, parameters) }
        val capturesLeft = relative.contains("\${")
        return DialogTarget(relative, absolute = if (capturesLeft) null else resolveExpectedPath(root, relative), derivedUndecided = false)
    }
    val originSegments = if (origin.startsWith(root)) root.relativize(origin).map { it.toString() } else emptyList()
    val segments = pattern.split('/').mapIndexed { index, segment ->
        val fromOrigin = originSegments.getOrNull(index)
        if (fromOrigin != null && readsDerived(segment) && namesOf(segment).all { name -> seeds[name]?.let { it == inputs[name] } == true }) {
            fromOrigin
        } else {
            replacePlaceholders(segment) { name -> valueOf(name, inputs, parameters) }
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

/** The capture and derived names [segment] reads. */
private fun namesOf(segment: String): List<String> =
    TOKEN.findAll(segment).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()

private fun valueOf(name: String, inputs: Map<String, String>, parameters: Map<String, ParameterModel>): String? {
    val parameter = parameters[name] ?: return inputs[name]?.takeIf { it.isNotBlank() }
    return expectedValueOf(parameter, inputs, parameters)
}
