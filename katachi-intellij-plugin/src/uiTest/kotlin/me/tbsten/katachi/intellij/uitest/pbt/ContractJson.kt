package me.tbsten.katachi.intellij.uitest.pbt

import me.tbsten.katachi.intellij.model.BranchModel
import me.tbsten.katachi.intellij.model.FilePreviewModel
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateModel

/**
 * [templates] as `templateDescription.json`, the shape katachiInternalTemplatesJson writes, so that
 * generated definitions go through the plugin's real parser and loader.
 */
internal fun contractJsonOf(templates: List<TemplateModel>): String = obj(
    "templates" to array(templates.map { template ->
        val summary = template.summary
        obj(
            "roleName" to str(summary.roleName),
            "title" to str(summary.title),
            "summary" to str(summary.summary),
            "parameterNames" to array(summary.parameterNames.map(::str)),
            "fileCount" to (summary.fileCount?.toString() ?: "null"),
            "captures" to array(template.detail?.captures.orEmpty().flatMap(::captureJson)),
        )
    }),
    "details" to array(templates.mapNotNull { it.detail }.map { detail ->
        obj(
            "roleName" to str(detail.roleName),
            "title" to str(detail.title),
            "summary" to str(detail.summary),
            "parameters" to array(detail.parameters.map(::parameterJson)),
            "files" to array(detail.files.map(::fileJson)),
            "branches" to array(detail.branches.map(::branchJson)),
            "exampleCommand" to str(detail.exampleCommand),
            "captures" to array(detail.captures.flatMap(::captureJson)),
        )
    }),
)

private fun parameterJson(parameter: ParameterModel): String = obj(
    "name" to str(parameter.name),
    "kind" to str(parameter.kindName),
    "typeName" to str(parameter.typeName),
    "default" to str(parameter.default),
    "acceptedValues" to array(
        when (parameter) {
            is ParameterModel.EnumParam -> parameter.acceptedValues
            is ParameterModel.BooleanParam -> listOf("true", "false")
            else -> emptyList()
        }.map(::str),
    ),
    "isRequired" to parameter.isRequired.toString(),
    "previewValue" to str(parameter.previewValue),
    "previewValueSource" to str(if (parameter is ParameterModel.StringParam) "Placeholder" else "StandIn"),
)

/** One entry per place, as katachi lists a capture used in several. */
private fun captureJson(capture: ParameterModel.CaptureParam): List<String> = capture.places.map { place ->
    obj(
        "name" to str(capture.name),
        "kind" to str(place.kindName),
        "pattern" to str(place.pattern),
        "position" to place.position.toString(),
    )
}

private fun fileJson(file: FilePreviewModel): String = obj(
    "fileName" to str(file.fileName),
    "path" to str(file.path),
    "unresolvedPatterns" to array(file.unresolvedPatterns.map(::str)),
    "content" to str(file.content),
)

private fun branchJson(branch: BranchModel): String = obj(
    "parameterName" to str(branch.parameterName),
    "value" to str(branch.value),
    "addedFiles" to array(branch.addedFiles.map(::str)),
    "removedFiles" to array(branch.removedFiles.map(::str)),
    "addedParameters" to array(branch.addedParameters.map(::parameterJson)),
    "removedParameters" to array(branch.removedParameters.map(::str)),
)

private fun obj(vararg members: Pair<String, String>): String =
    members.joinToString(", ", "{", "}") { (key, value) -> "${str(key)}: $value" }

private fun array(elements: List<String>): String = elements.joinToString(", ", "[", "]")

private fun str(value: String?): String {
    if (value == null) return "null"
    val escaped = buildString {
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
    }
    return "\"$escaped\""
}
