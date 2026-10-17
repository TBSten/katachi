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

/** `kind` of a `templateSetDetails[].members[].args[]` entry that is a capture of the member's role. */
internal const val SET_ARG_KIND_CAPTURE: String = "Capture"

/** One `templateSets[]` entry as the JSON has it. */
internal data class SetSummaryJson(
    /** The complete specifier, `feature.NewFeature`: what `--arg template=` takes. */
    val specifier: String,
    val name: String,
    val title: String,
    val summary: String?,
    val groupName: String?,
    val parameterNames: List<String>,
    val conflict: Boolean,
)

/** One `templateSetDetails[]` entry as the JSON has it. */
internal data class SetDetailJson(
    val specifier: String,
    val parameters: List<ParameterModel>,
    val members: List<SetMemberJson>,
    val exampleCommand: String,
)

/** One member of a [SetDetailJson]. */
internal data class SetMemberJson(
    val template: String,
    val title: String,
    val args: List<SetArgModel>,
    val files: List<FilePreviewModel>,
)

/**
 * The list row of a template set (design: the row's summary and detail are the set's, its files are
 * every member's files in declared order).
 *
 * [placements] is `modulePlacements[]` by template: a member whose file sits below a module capture
 * (`path` is `null`) gets its template's placement, in the set's parameter names ([placedInSetNames]).
 */
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

/**
 * A member's file as the set's row shows it, every name in the set's own words.
 *
 * Each capture of the member is replaced by the value the set passes it ([SetArgModel.previewValue]:
 * katachi wrote it with the set's placeholders, `${p}`, `${name}Main` or `Home`), so [FilePreviewModel.pattern],
 * [FilePreviewModel.fileName] and [FilePreviewModel.captures] describe the file the set writes. A file
 * with a [FilePreviewModel.path] already has them filled, so that `path` is its pattern. A file below
 * a module capture keeps its own pattern and gets [placement] in the set's names; when the set does not
 * pass the module key on as one of its parameters, or leaves a capture of the path without a value,
 * the file is left as it is and the place is decided when the set runs.
 */
private fun setFileOf(file: FilePreviewModel, args: List<SetArgModel>, placement: ModulePlacementModel?): FilePreviewModel {
    if (file.path != null) return inSetNames(file, file.path)
    val placed = placement?.let { placedInSetNames(it, args) } ?: return file
    val values = captureValuesOf(args)
    if (!placeholderNames(file.pattern).all { it in values }) return file
    return inSetNames(file, filledWith(file.pattern, values)).copy(modulePlacement = placed)
}

private fun inSetNames(file: FilePreviewModel, pattern: String): FilePreviewModel = file.copy(
    pattern = pattern,
    fileName = pattern.substringAfterLast('/'),
    captures = placeholderNames(pattern).distinct(),
)

/**
 * [placement] with the key's capture names and each path written in the set's names: the key is the
 * set's parameters that [args] pass its captures as they are, and every `${name}` of a path is the
 * value [args] give that capture. `null` if a key capture is not passed as a parameter, or a path
 * has a capture that [args] give no value.
 */
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

/** Every `${old}` of [text] written as [values] has it; all at once, so a value that holds a placeholder is not replaced again. */
private fun filledWith(text: String, values: Map<String, String>): String =
    placeholder.replace(text) { match -> values[match.groupValues[1]] ?: match.value }
