package me.tbsten.katachi.intellij.uitest.pbt

import me.tbsten.katachi.intellij.model.BranchModel
import me.tbsten.katachi.intellij.model.FilePreviewModel
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateModel

/**
 * [rows] (templates and template sets) as `templateDescription.json`, the shape katachiInternalTemplatesJson writes, so that
 * generated definitions go through the plugin's real parser and loader.
 */
internal fun contractJsonOf(rows: List<TemplateModel>): String {
    val templates = rows.filter { it.set == null }
    val sets = rows.filter { it.set != null }
    return obj(
        "templates" to array(templates.map { template ->
            val summary = template.summary
            obj(
                "template" to str(summary.template),
                "id" to str(summary.id),
                "title" to str(summary.title),
                "roleName" to str(summary.roleName),
                "summary" to str(summary.summary),
                "parameterNames" to array(summary.parameterNames.map(::str)),
                "conflict" to summary.conflict.toString(),
                "captures" to array(template.detail?.captures.orEmpty().flatMap(::captureJson)),
            )
        }),
        "details" to array(templates.mapNotNull { it.detail }.map { detail ->
            obj(
                "template" to str(detail.template),
                "id" to str(detail.id),
                "title" to str(detail.title),
                "roleName" to str(detail.roleName),
                "summary" to str(detail.summary),
                "parameters" to array(detail.parameters.map(::parameterJson)),
                "files" to array(detail.files.map(::fileJson)),
                "branches" to array(detail.branches.map(::branchJson)),
                "exampleCommand" to str(detail.exampleCommand),
                "captures" to array(detail.captures.flatMap(::captureJson)),
            )
        }),
        "modulePlacements" to array(sets.flatMap(::setPlacementsJson)),
        "templateSets" to array(sets.map(::setSummaryJson)),
        "templateSetDetails" to array(sets.mapNotNull(::setDetailJson)),
    )
}

/** A template set's entry of `templateSets[]`: its specifier, its members' specifiers, and whether its preview failed. */
private fun setSummaryJson(row: TemplateModel): String {
    val summary = row.summary
    return obj(
        "templateSet" to str(summary.template),
        "name" to str(row.set!!.name),
        "title" to str(summary.title),
        "summary" to str(summary.summary),
        "groupName" to str(summary.template.substringBeforeLast('.', "").ifEmpty { null }),
        "parameterNames" to array(summary.parameterNames.map(::str)),
        "members" to array(row.set.members.map { str(it.template) }),
        "conflict" to summary.conflict.toString(),
    )
}

/** The set's entry of `templateSetDetails[]`; none when its preview failed. */
private fun setDetailJson(row: TemplateModel): String? {
    val detail = row.detail ?: return null
    return obj(
        "templateSet" to str(row.summary.template),
        "name" to str(row.set!!.name),
        "title" to str(detail.title),
        "summary" to str(detail.summary),
        "groupName" to str(row.summary.template.substringBeforeLast('.', "").ifEmpty { null }),
        "parameters" to array(detail.parameters.map(::parameterJson)),
        "members" to array(row.set.members.map { member ->
            obj(
                "template" to str(member.template),
                "title" to str(member.title),
                "args" to array(member.args.map { arg ->
                    obj(
                        "name" to str(arg.name),
                        "kind" to str(if (arg.isCapture) "Capture" else "Parameter"),
                        "previewValue" to str(arg.previewValue),
                        "parameter" to str(arg.parameter),
                    )
                }),
                "files" to array(member.files.map(::fileJson)),
            )
        }),
        "exampleCommand" to str(detail.exampleCommand),
    )
}

/** `modulePlacements[]` entries of the members of [row] that sit below a module capture, keyed by the member's template. */
private fun setPlacementsJson(row: TemplateModel): List<String> = row.set!!.members.mapNotNull { member ->
    val placement = member.files.firstNotNullOfOrNull { it.modulePlacement } ?: return@mapNotNull null
    obj(
        "template" to str(member.template),
        "modulePattern" to str(placement.modulePattern),
        "captureNames" to array(placement.captureNames.map(::str)),
        "modules" to array(placement.modules.map { module ->
            obj(
                "values" to array(module.values.map(::str)),
                "modulePath" to str(module.modulePath),
                "directory" to str(module.directory),
                "path" to str(module.path),
            )
        }),
    )
}

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
        "segment" to str(place.segment),
        "position" to place.position.toString(),
    )
}

private fun fileJson(file: FilePreviewModel): String = obj(
    "pattern" to str(file.pattern),
    "fileName" to str(file.fileName),
    "path" to str(file.path),
    "captures" to array(file.captures.map(::str)),
    "parameters" to array(file.parameters.map(::str)),
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
