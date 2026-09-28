package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.BranchModel
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel

/** One line of the inline form: a field, or a parameter folded because its branch is not taken (E-08). */
internal sealed interface FieldSlot {
    val parameter: ParameterModel

    data class Shown(override val parameter: ParameterModel) : FieldSlot

    /**
     * Shown as a grey line "implName (when withImpl is [whenValue])". Its input is kept but not sent,
     * since katachi rejects a name the run does not declare.
     */
    data class Collapsed(
        override val parameter: ParameterModel,
        val controllerName: String,
        val whenValue: String,
    ) : FieldSlot
}

/**
 * The value a parameter takes for the branch rules: the input, else the default, else the preview
 * value (a default that reads another parameter is not a branch value).
 */
internal fun branchValueOf(parameter: ParameterModel, inputs: Map<String, String>): String {
    val input = inputs[parameter.name]
    if (!input.isNullOrBlank()) return input.trim()
    val default = parameter.default
    return if (default != null && !default.contains("\${")) default else parameter.previewValue
}

/** The branches [inputs] take: each one's parameter holds a value other than its preview value. */
internal fun activeBranchesOf(detail: TemplateDetailModel, inputs: Map<String, String>): List<BranchModel> {
    val parameters = detail.parameters.associateBy { it.name }
    return detail.branches.filter { branch ->
        val controller = parameters[branch.parameterName] ?: return@filter false
        branchValueOf(controller, inputs) == branch.value
    }
}

/**
 * The form's lines in order: the captures first, since they decide where the files go (a capture
 * is never in a branch), then the preview's parameters, each followed by the parameters its branches
 * take away and then those they add, so that what an `if` holds sits under the field that controls
 * it (katachi lists a parameter declared in an `if` the preview takes at the end). A parameter a
 * taken branch removes, and one an untaken branch adds, is [FieldSlot.Collapsed].
 *
 * Only one parameter differing at a time is known (katachi previews branches one by one), so a
 * parameter that needs two values at once never shows; generation reports it instead.
 */
internal fun fieldSlotsOf(detail: TemplateDetailModel, inputs: Map<String, String>): List<FieldSlot> {
    val active = activeBranchesOf(detail, inputs).toSet()
    val baseNames = detail.parameters.map { it.name }.toSet()
    val removedBy = HashMap<String, ParameterModel>()
    for (branch in active) {
        val controller = detail.parameters.firstOrNull { it.name == branch.parameterName } ?: continue
        for (name in branch.removedParameters) removedBy.putIfAbsent(name, controller)
    }

    // The first controller whose branch removes a parameter: the parameter goes under it.
    val controllerOf = LinkedHashMap<String, String>()
    for (branch in detail.branches) {
        for (name in branch.removedParameters) {
            if (name != branch.parameterName && branch.parameterName in baseNames) controllerOf.putIfAbsent(name, branch.parameterName)
        }
    }

    val slots = mutableListOf<FieldSlot>()
    val emitted = HashSet<String>()
    for (capture in detail.captures) {
        if (emitted.add(capture.name)) slots += FieldSlot.Shown(capture)
    }
    fun emit(parameter: ParameterModel) {
        if (!emitted.add(parameter.name)) return
        val remover = removedBy[parameter.name]
        slots += if (remover == null) {
            FieldSlot.Shown(parameter)
        } else {
            FieldSlot.Collapsed(parameter, controllerName = remover.name, whenValue = remover.previewValue)
        }
        detail.parameters.filter { controllerOf[it.name] == parameter.name }.forEach(::emit)
        slots += addedSlotsOf(parameter, detail.branches, active, baseNames, emitted)
    }
    // A controlled parameter waits for its controller, unless the controller never comes (a cycle).
    detail.parameters.filter { it.name !in controllerOf }.forEach(::emit)
    detail.parameters.forEach(::emit)
    return slots
}

/** The parameters that [controller]'s branches add, to go right after it. */
private fun addedSlotsOf(
    controller: ParameterModel,
    branches: List<BranchModel>,
    active: Set<BranchModel>,
    baseNames: Set<String>,
    emitted: MutableSet<String>,
): List<FieldSlot> {
    val own = branches.filter { it.parameterName == controller.name }
    val names = own.flatMap { branch -> branch.addedParameters.map { it.name } }.distinct()
        .filter { it !in baseNames && it !in emitted }
    return names.map { name ->
        emitted += name
        val activeBranch = own.firstOrNull { branch -> branch in active && branch.addedParameters.any { it.name == name } }
        if (activeBranch != null) {
            FieldSlot.Shown(activeBranch.addedParameters.first { it.name == name })
        } else {
            val branch = own.first { branch -> branch.addedParameters.any { it.name == name } }
            FieldSlot.Collapsed(branch.addedParameters.first { it.name == name }, controller.name, branch.value)
        }
    }
}

/** The parameters the form shows now, in order: the ones generation sends. */
internal fun shownParametersOf(detail: TemplateDetailModel, inputs: Map<String, String>): List<ParameterModel> =
    fieldSlotsOf(detail, inputs).filterIsInstance<FieldSlot.Shown>().map { it.parameter }
