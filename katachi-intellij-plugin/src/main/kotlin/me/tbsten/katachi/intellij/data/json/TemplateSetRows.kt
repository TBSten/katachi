package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.FilePreviewModel
import me.tbsten.katachi.intellij.model.ModulePlacementModel
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.SetArgModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateSetMemberModel
import me.tbsten.katachi.intellij.model.TemplateSetModel
import me.tbsten.katachi.intellij.model.TemplateSummaryModel

internal const val SET_ARG_KIND_CAPTURE: String = "Capture"

internal data class SetSummaryJson(
    val specifier: String,
    val name: String,
    val title: String,
    val summary: String?,
    val groupName: String?,
    val parameterNames: List<String>,
    val conflict: Boolean,
)

internal data class SetDetailJson(
    val specifier: String,
    val parameters: List<ParameterModel>,
    val members: List<SetMemberJson>,
    val exampleCommand: String,
)

internal data class SetMemberJson(
    val template: String,
    val title: String,
    val args: List<SetArgModel>,
    val files: List<FilePreviewModel>,
)

internal fun templateSetRow(
    summary: SetSummaryJson,
    detail: SetDetailJson?,
    placements: Map<String, ModulePlacementModel>,
): TemplateModel {
    // roleName is the specifier, so the group heading (everything before the last '.') is the set's group.
    val row = TemplateSummaryModel(
        template = summary.specifier,
        id = null,
        title = summary.title,
        roleName = summary.specifier,
        summary = summary.summary,
        parameterNames = summary.parameterNames,
        conflict = summary.conflict,
    )
    if (detail == null) return TemplateModel(summary = row, detail = null, set = TemplateSetModel(summary.name, emptyList()))
    val members = detail.members.map { member ->
        TemplateSetMemberModel(
            template = member.template,
            title = member.title,
            args = member.args,
            files = member.files.map { setFileOf(it, member.args, placements[member.template]) },
        )
    }
    val model = TemplateDetailModel(
        template = summary.specifier,
        id = null,
        title = summary.title,
        roleName = summary.specifier,
        summary = summary.summary,
        parameters = detail.parameters,
        files = members.flatMap { it.files },
        branches = emptyList(),
        exampleCommand = detail.exampleCommand,
    )
    return TemplateModel(summary = row, detail = model, set = TemplateSetModel(summary.name, members))
}

private fun setFileOf(file: FilePreviewModel, args: List<SetArgModel>, placement: ModulePlacementModel?): FilePreviewModel {
    if (file.path != null) return inSetNames(file, file.path)
    val placed = placement?.let { placedInSetNames(it, args) } ?: return unexpressed(file)
    val values = captureValuesOf(args)
    if (!placeholderNames(file.pattern).all { it in values }) return unexpressed(file)
    return inSetNames(file, filledWith(file.pattern, values)).copy(modulePlacement = placed)
}

private fun unexpressed(file: FilePreviewModel): FilePreviewModel = file.copy(captures = emptyList())

private fun inSetNames(file: FilePreviewModel, pattern: String): FilePreviewModel = file.copy(
    pattern = pattern,
    fileName = pattern.substringAfterLast('/'),
    captures = placeholderNames(pattern).distinct(),
)

internal fun placedInSetNames(placement: ModulePlacementModel, args: List<SetArgModel>): ModulePlacementModel? {
    val names = placement.captureNames.map { key ->
        args.firstOrNull { it.isCapture && it.name == key }?.parameter ?: return null
    }
    val values = captureValuesOf(args)
    if (placement.modules.any { module -> !placeholderNames(module.path).all { it in values } }) return null
    return ModulePlacementModel(
        modulePattern = placement.modulePattern,
        captureNames = names,
        modules = placement.modules.map { it.copy(path = filledWith(it.path, values)) },
    )
}

private fun captureValuesOf(args: List<SetArgModel>): Map<String, String> =
    args.filter { it.isCapture }.associate { it.name to it.previewValue }

private val placeholder = Regex("""\$\{([^}]+)}""")

private fun placeholderNames(text: String): List<String> = placeholder.findAll(text).map { it.groupValues[1] }.toList()

private fun filledWith(text: String, values: Map<String, String>): String =
    placeholder.replace(text) { match -> values[match.groupValues[1]] ?: match.value }
