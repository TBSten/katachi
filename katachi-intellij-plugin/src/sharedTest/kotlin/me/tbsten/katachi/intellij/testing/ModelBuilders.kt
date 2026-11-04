package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.model.BranchModel
import me.tbsten.katachi.intellij.model.CapturePlace
import me.tbsten.katachi.intellij.model.FilePreviewModel
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateSummaryModel

internal fun stringParam(name: String, default: String? = null) =
    ParameterModel.StringParam(name, "String", default, default == null, "\${$name}")

internal fun intParam(name: String, default: String? = null) =
    ParameterModel.IntParam(name, "Int", default, default == null, default ?: "0")

internal fun booleanParam(name: String, default: String? = "true") =
    ParameterModel.BooleanParam(name, "Boolean", default, default == null, default ?: "true")

internal fun enumParam(name: String, values: List<String>, default: String? = values.first(), typeName: String = "Kind") =
    ParameterModel.EnumParam(name, typeName, default, default == null, default ?: values.first(), values)

/** A capture at one place: a `/` level of a file pattern, or with [module] the `*` of a module key. */
internal fun capture(name: String, pattern: String = "feature/*/*.kt", position: Int = 1, module: Boolean = false, segment: String = "\${$name}") =
    ParameterModel.CaptureParam(
        name,
        listOf(CapturePlace(if (module) CapturePlace.KIND_MODULE else CapturePlace.KIND_PATH, pattern, position, segment)),
    )

internal fun file(fileName: String, path: String? = "src/$fileName", content: String = "", pattern: String = path ?: fileName, captures: List<String> = emptyList(), parameters: List<String> = emptyList()) =
    FilePreviewModel(pattern, fileName, path, captures, parameters, content)

internal fun branch(
    parameterName: String,
    value: String,
    addedFiles: List<String> = emptyList(),
    removedFiles: List<String> = emptyList(),
    addedParameters: List<ParameterModel> = emptyList(),
    removedParameters: List<String> = emptyList(),
) = BranchModel(parameterName, value, addedFiles, removedFiles, addedParameters, removedParameters)

/**
 * A template of role [roleName], selected by [template] (defaults to the role name alone, as a
 * role with one template needs no `id`).
 */
internal fun template(
    roleName: String,
    parameters: List<ParameterModel> = listOf(stringParam("name")),
    files: List<FilePreviewModel> = listOf(file("\${name}.kt")),
    branches: List<BranchModel> = emptyList(),
    title: String? = null,
    summary: String? = null,
    id: String? = null,
    template: String = id?.let { "$roleName.$it" } ?: roleName,
    conflict: Boolean = false,
    captures: List<ParameterModel.CaptureParam> = emptyList(),
): TemplateModel {
    val shownTitle = title ?: id ?: roleName
    return TemplateModel(
        summary = TemplateSummaryModel(template, id, shownTitle, roleName, summary, parameters.map { it.name }, conflict, captures.map { it.name }),
        detail = TemplateDetailModel(
            template,
            id,
            shownTitle,
            roleName,
            summary,
            parameters,
            files,
            branches,
            "./gradlew katachiTemplate --arg template=$template",
            captures,
        ),
    )
}

internal fun previewFailed(roleName: String, id: String? = null): TemplateModel {
    val template = id?.let { "$roleName.$it" } ?: roleName
    return TemplateModel(TemplateSummaryModel(template, id, id ?: roleName, roleName, null, listOf("name"), conflict = true), detail = null)
}

internal fun rows(vararg templates: TemplateModel, module: KatachiModule = module()): List<ModuleTemplate> =
    templates.map { ModuleTemplate(module, it) }

/** [this] as [T], failing the test with what it was instead (the codebase does not use `as`). */
internal inline fun <reified T> Any?.cast(): T = this as? T ?: throw AssertionError("expected ${T::class.simpleName} but was $this")
