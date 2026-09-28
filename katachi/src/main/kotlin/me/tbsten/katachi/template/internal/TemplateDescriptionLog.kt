package me.tbsten.katachi.template.internal

import me.tbsten.katachi.template.PreviewValueSource
import me.tbsten.katachi.template.TemplateCaptureKind
import me.tbsten.katachi.template.TemplateCapturePreview
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateFilePreview
import me.tbsten.katachi.template.TemplateList
import me.tbsten.katachi.template.TemplateParameterKind
import me.tbsten.katachi.template.TemplateParameterPreview

/**
 * [list] as the lines `katachiTemplates` prints: one block per template, then how to go further.
 *
 * Lines rather than one string, because the run prefixes each logged line with its processor's
 * key: a multi-line message would lose the prefix after its first line.
 */
internal fun templateListLines(list: TemplateList): List<String> = buildList {
    if (list.templates.isEmpty()) {
        add("No role declares a template { }.")
        return@buildList
    }
    add("${list.templates.size} ${plural(list.templates.size, "template")}:")
    for (template in list.templates) {
        add("- ${headingOf(template.roleName, template.title)}")
        template.summary?.let { add("    $it") }
        val parameters = template.parameterNames.ifEmpty { listOf("(none)") }
        add("    parameters: ${parameters.joinToString(", ")}")
        if (template.captures.isNotEmpty()) {
            add("    captures: ${template.captures.map { it.name }.distinct().joinToString(", ")}")
        }
        add("    files: ${template.fileCount ?: "(the preview failed; see katachiTemplates --arg roleName=${template.roleName})"}")
    }
    add(
        "Show one with `./gradlew katachiTemplates --arg roleName=<role>`, and generate it with " +
            "`./gradlew katachiTemplate --arg roleName=<role> --arg <parameter>=<value>`.",
    )
}

/** [detail] as the lines `katachiTemplates --arg roleName=...` prints. */
internal fun templateDetailLines(detail: TemplateDetail): List<String> = buildList {
    add("Template of ${headingOf(detail.roleName, detail.title)}")
    detail.summary?.let { add("  $it") }

    add("")
    if (detail.parameters.isEmpty()) {
        add("Parameters: none")
    } else {
        add("Parameters:")
        detail.parameters.forEach { add("  ${parameterLine(it)}") }
    }

    if (detail.captures.isNotEmpty()) {
        add("")
        add("Captures (--arg values that choose the directory):")
        detail.captures.forEach { add("  ${captureLine(it)}") }
    }

    add("")
    add("Previewed with: ${previewValuesLine(detail.parameters)}")
    add("Files (paths relative to the project root):")
    for (file in detail.files) addAll(fileLines(file))

    val switchable = detail.parameters.filter { it.acceptedValues.isNotEmpty() }
    if (switchable.isNotEmpty()) {
        add("")
        if (detail.branches.isEmpty()) {
            add("No other value of ${switchable.joinToString(", ") { it.name }} changes which files are produced.")
        } else {
            add("Other values change which files are produced:")
            for (branch in detail.branches) {
                val changes = buildList {
                    if (branch.addedFiles.isNotEmpty()) add("adds ${branch.addedFiles.joinToString(", ")}")
                    if (branch.removedFiles.isNotEmpty()) add("leaves out ${branch.removedFiles.joinToString(", ")}")
                    if (branch.addedParameters.isNotEmpty()) {
                        add("adds parameter ${branch.addedParameters.joinToString(", ") { it.name }}")
                    }
                    if (branch.removedParameters.isNotEmpty()) {
                        add("drops parameter ${branch.removedParameters.joinToString(", ")}")
                    }
                }
                add("  --arg ${branch.parameterName}=${branch.value}: ${changes.joinToString("; ")}")
            }
        }
    }

    add("")
    add("Generate it with:")
    add("  ${detail.exampleCommand}")
}

/** `name: String, required` / `withImpl: Boolean, default true, accepts true or false`. */
private fun parameterLine(parameter: TemplateParameterPreview): String = buildString {
    append(parameter.name).append(": ").append(parameter.typeName)
    val default = parameter.default
    if (default == null) append(", required") else append(", default ").append(quotedIfString(parameter, default))
    if (parameter.acceptedValues.isNotEmpty()) {
        append(", accepts ").append(parameter.acceptedValues.joinToString(" | "))
    }
}

/** A path capture as `feature: level 2 of <file pattern>`, a module capture as `feature: wildcard 1 of module :feature:*, ...`. */
private fun captureLine(capture: TemplateCapturePreview): String = when (capture.kind) {
    TemplateCaptureKind.PathCapture -> "${capture.name}: level ${capture.position + 1} of ${capture.pattern}"
    TemplateCaptureKind.ModuleCapture ->
        "${capture.name}: wildcard ${capture.position + 1} of module ${capture.pattern}, an existing module"
}

/** `name=${name} (placeholder), withImpl=true (default)`. */
private fun previewValuesLine(parameters: List<TemplateParameterPreview>): String {
    if (parameters.isEmpty()) return "no parameters"
    return parameters.joinToString(", ") { parameter ->
        val why = when (parameter.previewValueSource) {
            PreviewValueSource.Placeholder -> "placeholder"
            PreviewValueSource.Default -> "default"
            PreviewValueSource.StandIn -> "stand-in, no default"
        }
        "${parameter.name}=${parameter.previewValue} ($why)"
    }
}

private fun fileLines(file: TemplateFilePreview): List<String> = buildList {
    val path = file.path
    if (path != null) {
        add("  $path")
    } else {
        add(
            "  ${file.fileName} -- no single directory in the preview: " +
                "${file.unresolvedPatterns.joinToString(", ")} has a wildcard only a run's " +
                "capture value, or nothing, can fill in",
        )
    }
    for (line in file.content.trimEnd('\n').lines()) add("    | $line")
}

private fun headingOf(roleName: String, title: String?): String =
    if (title == null) roleName else "$roleName ($title)"

private fun quotedIfString(parameter: TemplateParameterPreview, value: String): String =
    if (parameter.kind == TemplateParameterKind.StringParameter) "\"$value\"" else value

private fun plural(count: Int, word: String): String = if (count == 1) word else "${word}s"
