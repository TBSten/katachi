package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.presentation.argValueOf
import me.tbsten.katachi.intellij.presentation.shownParametersOf

internal data class TemplateArgsEntry(val template: String, val detail: TemplateDetailModel, val inputs: Map<String, String>)

internal fun templateArgsOf(entries: List<TemplateArgsEntry>, onExisting: OnExistingChoice): List<Pair<String, String>> {
    require(entries.isNotEmpty()) { "templateArgsOf needs at least one entry" }
    val args = mutableListOf(TEMPLATE to entries.joinToString(",") { it.template }, ON_EXISTING to onExisting.argValue)
    val sent = mutableSetOf<String>()
    for (entry in entries) {
        for (parameter in shownParametersOf(entry.detail, entry.inputs)) {
            if (!sent.add(parameter.name)) continue
            val input = entry.inputs[parameter.name]
            val value = when {
                !input.isNullOrBlank() -> argValueOf(parameter, input)
                parameter is ParameterModel.BooleanParam && parameter.default == null -> "true"
                else -> null
            } ?: continue
            args += parameter.name to value
        }
    }
    return args
}

internal fun templateArgsOf(
    template: String,
    detail: TemplateDetailModel,
    inputs: Map<String, String>,
    onExisting: OnExistingChoice,
): List<Pair<String, String>> = templateArgsOf(listOf(TemplateArgsEntry(template, detail, inputs)), onExisting)

internal fun templateInvocationOf(module: KatachiModule, args: List<Pair<String, String>>): GradleTaskInvocation =
    GradleTaskInvocation(module.taskPath(KatachiModule.TEMPLATE_TASK), args)

private const val TEMPLATE = "template"
private const val ON_EXISTING = "onExisting"
