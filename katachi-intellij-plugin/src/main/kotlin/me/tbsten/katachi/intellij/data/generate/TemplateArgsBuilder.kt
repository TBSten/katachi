package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.presentation.argValueOf
import me.tbsten.katachi.intellij.presentation.shownParametersOf

/** One checked row's share of a combined `katachiTemplate` run: its complete specifier, detail and current field values. */
internal data class TemplateArgsEntry(val template: String, val detail: TemplateDetailModel, val inputs: Map<String, String>)

/**
 * The `--arg`s of one `katachiTemplate` run (design draft section 6, "IDE の複数選択"):
 *
 * - `template` ([entries]' complete specifiers, comma joined so a run covers every checked row of
 *   the module in one build) and `onExisting` always come first;
 * - then each entry's shown parameters and captures, in [entries] order, a name sent only once: two
 *   entries sharing a linked field (E-14) already agree on its value, so the first is enough. An
 *   empty field with a default is left out so katachi evaluates the default itself (E-22); a folded
 *   conditional parameter is left out because katachi rejects a name the run does not declare (E-08).
 * - A Boolean is sent when touched or when it has no default (then it is `true`, as in katachi's
 *   preview).
 */
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

/** [templateArgsOf] for one checked row alone. */
internal fun templateArgsOf(
    template: String,
    detail: TemplateDetailModel,
    inputs: Map<String, String>,
    onExisting: OnExistingChoice,
): List<Pair<String, String>> = templateArgsOf(listOf(TemplateArgsEntry(template, detail, inputs)), onExisting)

/** The run of [args] in [module]'s `katachiTemplate` (E-05: always the templates' own module). */
internal fun templateInvocationOf(module: KatachiModule, args: List<Pair<String, String>>): GradleTaskInvocation =
    GradleTaskInvocation(module.taskPath(KatachiModule.TEMPLATE_TASK), args)

private const val TEMPLATE = "template"
private const val ON_EXISTING = "onExisting"
