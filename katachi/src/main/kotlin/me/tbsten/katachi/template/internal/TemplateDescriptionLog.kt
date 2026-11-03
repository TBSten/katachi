package me.tbsten.katachi.template.internal

import me.tbsten.katachi.template.PreviewValueSource
import me.tbsten.katachi.template.TemplateCaptureKind
import me.tbsten.katachi.template.TemplateCapturePreview
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateFilePreview
import me.tbsten.katachi.template.TemplateList
import me.tbsten.katachi.template.TemplateParameterKind
import me.tbsten.katachi.template.TemplateParameterPreview
import me.tbsten.katachi.template.TemplateSetDetail
import me.tbsten.katachi.template.TemplateSetSummary

internal fun templateListLines(list: TemplateList): List<String> = buildList {
    if (list.templates.isEmpty() && list.templateSets.isEmpty()) {
        add("No declaration attaches a template { }.")
        return@buildList
    }
    if (list.templates.isNotEmpty()) addAll(templateLines(list))
    if (list.templateSets.isNotEmpty()) addAll(templateSetLines(list.templateSets))
}

private fun templateLines(list: TemplateList): List<String> = buildList {
    add("${list.templates.size} ${plural(list.templates.size, "template")}:")
    for (template in list.templates) {
        add("- ${headingOf(template.template, template.title)}")
        template.summary?.let { add("    $it") }
        val parameters = template.parameterNames.ifEmpty { listOf("(none)") }
        add("    parameters: ${parameters.joinToString(", ")}")
        if (template.captures.isNotEmpty()) {
            add("    captures: ${template.captures.map { it.name }.distinct().joinToString(", ")}")
        }
        if (template.conflict) {
            add("    (the preview failed; see katachiTemplates --arg template=${template.template})")
        }
    }
    add(
        "Show one with `./gradlew katachiTemplates --arg template=<template>`, and generate it " +
            "with `./gradlew katachiTemplate --arg template=<template> --arg <parameter>=<value>`.",
    )
}

private fun templateSetLines(sets: List<TemplateSetSummary>): List<String> = buildList {
    add("Template sets (${sets.size}):")
    for (set in sets) {
        val title = set.title.takeIf { it != set.name && it != set.templateSet }
        val parameters = set.parameterNames.joinToString(" ") { "--arg $it" }
        add(listOfNotNull("  ${set.templateSet}", title, parameters.ifEmpty { null }).joinToString("  "))
        add("    members: ${set.members.joinToString(", ")}")
        set.summary?.let { add("    $it") }
        if (set.conflict) {
            add("    (the preview failed; see katachiTemplates --arg template=${set.templateSet})")
        }
    }
}

internal fun templateSetDetailLines(detail: TemplateSetDetail): List<String> = buildList {
    add("Template set ${headingOf(detail.templateSet, detail.title)}")
    detail.summary?.let { add("  $it") }

    add("")
    if (detail.parameters.isEmpty()) {
        add("Parameters: none")
    } else {
        add("Parameters:")
        detail.parameters.forEach { add("  ${parameterLine(it)}") }
    }

    for (member in detail.members) {
        add("")
        add("member ${headingOf(member.template, member.title)}")
        for (arg in member.args) add("  arg ${arg.name} <- ${arg.previewValue}")
        add("  File (path relative to the project root):")
        fileLines(member.files.single()).forEach { add("  $it") }
    }

    add("")
    add("Run it with, putting the directory or module each <slot> stands for in its place:")
    add("  ${detail.exampleCommand}")
}

internal fun templateDetailLines(detail: TemplateDetail): List<String> = buildList {
    add("Template ${headingOf(detail.template, detail.title)}")
    add("  role: ${detail.roleName}")
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
        val seen = mutableSetOf<String>()
        for (capture in detail.captures) {
            // The first plain occurrence of a name takes the `--arg`; every later one reads it.
            val repeated = capture.transform == null && !seen.add(capture.name)
            add("  ${captureLine(capture, repeated)}")
        }
    }

    add("")
    add("Previewed with: ${previewValuesLine(detail.parameters)}")
    add("File (path relative to the project root):")
    addAll(fileLines(detail.files.single()))

    val switchable = detail.parameters.filter { it.acceptedValues.isNotEmpty() }
    if (switchable.isNotEmpty()) {
        add("")
        if (detail.branches.isEmpty()) {
            add("No other value of ${switchable.joinToString(", ") { it.name }} changes the content or the parameters.")
        } else {
            add("Other values change the content or the parameters:")
            for (branch in detail.branches) {
                val paramChanges = buildList {
                    if (branch.addedParameters.isNotEmpty()) {
                        add("adds parameter ${branch.addedParameters.joinToString(", ") { it.name }}")
                    }
                    if (branch.removedParameters.isNotEmpty()) {
                        add("drops parameter ${branch.removedParameters.joinToString(", ")}")
                    }
                }
                val changes = paramChanges.ifEmpty { listOf("changes the content") }
                add("  --arg ${branch.parameterName}=${branch.value}: ${changes.joinToString("; ")}")
            }
        }
    }

    add("")
    add("Generate it with, putting the directory or module each <capture> stands for in its place:")
    add("  ${detail.exampleCommand}")
}

private fun parameterLine(parameter: TemplateParameterPreview): String = buildString {
    append(parameter.name).append(": ").append(parameter.typeName)
    val default = parameter.default
    if (default == null) append(", required") else append(", default ").append(quotedIfString(parameter, default))
    if (parameter.acceptedValues.isNotEmpty()) {
        append(", accepts ").append(parameter.acceptedValues.joinToString(" | "))
    }
}

private fun captureLine(capture: TemplateCapturePreview, repeated: Boolean): String = when (capture.kind) {
    TemplateCaptureKind.PathCapture -> {
        val transform = capture.transform
        if (transform != null) {
            "${capture.name}.$transform: level ${capture.position + 1} of ${capture.pattern}, " +
                "derived from ${capture.name} (no --arg of its own)"
        } else {
            "${capture.name}: level ${capture.position + 1} of ${capture.pattern}" +
                (if (repeated) " (the same value)" else "")
        }
    }
    TemplateCaptureKind.ModuleCapture ->
        "${capture.name}: wildcard ${capture.position + 1} of module ${capture.pattern}, an existing module" +
            (if (repeated) " (the same value)" else "")
}

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
        add("  ${file.fileName} -- below a module capture, whose module a run's values pick: ${file.pattern}")
    }
    for (line in file.content.trimEnd('\n').lines()) add("    | $line")
}

private fun headingOf(specifier: String, title: String): String =
    if (title == specifier) specifier else "$specifier ($title)"

private fun quotedIfString(parameter: TemplateParameterPreview, value: String): String =
    if (parameter.kind == TemplateParameterKind.StringParameter) "\"$value\"" else value

private fun plural(count: Int, word: String): String = if (count == 1) word else "${word}s"
