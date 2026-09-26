package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.model.BranchModel
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

internal fun file(fileName: String, path: String? = "src/$fileName", content: String = "", patterns: List<String> = emptyList()) =
    FilePreviewModel(fileName, path, patterns, content)

internal fun branch(
    parameterName: String,
    value: String,
    addedFiles: List<String> = emptyList(),
    removedFiles: List<String> = emptyList(),
    addedParameters: List<ParameterModel> = emptyList(),
    removedParameters: List<String> = emptyList(),
) = BranchModel(parameterName, value, addedFiles, removedFiles, addedParameters, removedParameters)

internal fun template(
    roleName: String,
    parameters: List<ParameterModel> = listOf(stringParam("name")),
    files: List<FilePreviewModel> = listOf(file("\${name}.kt")),
    branches: List<BranchModel> = emptyList(),
    title: String? = null,
    summary: String? = null,
    fileCount: Int? = files.size,
): TemplateModel = TemplateModel(
    summary = TemplateSummaryModel(roleName, title, summary, parameters.map { it.name }, fileCount),
    detail = TemplateDetailModel(roleName, title, summary, parameters, files, branches, "./gradlew katachiTemplate --arg roleName=$roleName"),
)

internal fun previewFailed(roleName: String): TemplateModel =
    TemplateModel(TemplateSummaryModel(roleName, null, null, listOf("name"), null), detail = null)

internal fun rows(vararg templates: TemplateModel, module: KatachiModule = module()): List<ModuleTemplate> =
    templates.map { ModuleTemplate(module, it) }

/** [this] as [T], failing the test with what it was instead (the codebase does not use `as`). */
internal inline fun <reified T> Any?.cast(): T = this as? T ?: throw AssertionError("expected ${T::class.simpleName} but was $this")
