package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.presentation.argValueOf
import me.tbsten.katachi.intellij.presentation.shownParametersOf

/**
 * The `--arg`s of one `katachiTemplate` run (spec 06 "引数の組み立て"):
 *
 * - `roleName` (always qualified, so same-named roles in two groups stay apart) and `onExisting`
 *   always come first;
 * - then the shown parameters in form order. An empty field with a default is left out so katachi
 *   evaluates the default itself (E-22); a folded conditional parameter is left out because katachi
 *   rejects a name the run does not declare (E-08).
 * - A Boolean is sent when touched or when it has no default (then it is `true`, as in katachi's
 *   preview).
 */
internal fun templateArgsOf(
    roleName: String,
    detail: TemplateDetailModel,
    inputs: Map<String, String>,
    onExisting: OnExistingChoice,
): List<Pair<String, String>> {
    val args = mutableListOf(ROLE_NAME to roleName, ON_EXISTING to onExisting.argValue)
    for (parameter in shownParametersOf(detail, inputs)) {
        val input = inputs[parameter.name]
        val value = when {
            !input.isNullOrBlank() -> argValueOf(parameter, input)
            parameter is ParameterModel.BooleanParam && parameter.default == null -> "true"
            else -> null
        } ?: continue
        args += parameter.name to value
    }
    return args
}

/** The run of [roleName] in [module]'s `katachiTemplate` (E-05: always the template's own module). */
internal fun templateInvocationOf(module: KatachiModule, args: List<Pair<String, String>>): GradleTaskInvocation =
    GradleTaskInvocation(module.taskPath(KatachiModule.TEMPLATE_TASK), args)

private const val ROLE_NAME = "roleName"
private const val ON_EXISTING = "onExisting"
