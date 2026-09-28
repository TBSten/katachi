package me.tbsten.katachi.intellij.uitest.dialog

import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.FieldSlot
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogListNotice
import me.tbsten.katachi.intellij.uitest.pbt.DialogOp

/** What the machine remembers of the template last on screen: its inputs carry over to the next (decision 4). */
internal class LastShown(val template: ModuleTemplate, val values: Map<String, String?>)

/** Whether the template select box changed the selection: the seeds are asked again then. */
internal fun DialogView.selectionMovedFrom(before: DialogView): Boolean = state.selectedTemplate != before.state.selectedTemplate

/**
 * What the step [op] may have changed and what it may not. [before] and [after] are the states around it,
 * [lastShown] the template on screen before it (the one the inputs come from).
 */
internal fun transitionProblemsOf(op: DialogOp, before: DialogView, after: DialogView, lastShown: LastShown?, typed: Pair<String, String>?): List<String> {
    val problems = mutableListOf<String>()
    val b = before.state
    val a = after.state
    fun unchanged(what: String, same: Boolean) { if (!same) problems += "$op changed $what" }

    when (op) {
        is DialogOp.Type, is DialogOp.Clear -> {
            unchanged("the selection", a.selectedTemplate == b.selectedTemplate && a.candidates == b.candidates)
            if (typed != null) {
                val (name, text) = typed
                val now = a.fields.firstOrNull { it.name == name }?.value
                if (now != text) problems += "typed '$text' into $name and it reads '$now'"
                unchanged("another field", a.fields.filter { it.name != name }.map { it.name to it.value } == b.fields.filter { it.name != name }.map { it.name to it.value })
            }
        }
        is DialogOp.SelectTemplate, is DialogOp.SelectDefinition -> problems += switchProblemsOf(op, before, after, lastShown, expectNotice = null)
        is DialogOp.Shrink, DialogOp.Restore, is DialogOp.Rotate -> problems += listChangeProblemsOf(before, after, lastShown)
        is DialogOp.ExternalWrite, DialogOp.Generate -> {
            unchanged("the selection", a.selectedTemplate == b.selectedTemplate && a.candidates == b.candidates && a.selectedDefinition == b.selectedDefinition)
            unchanged("the inputs", a.fields.map { it.name to it.value } == b.fields.map { it.name to it.value })
        }
        is DialogOp.Cancel -> Unit
    }
    return problems
}

/** After a switch (decision 4): the origin's captures are asked of the real index, the others carry over by name, kind and type. */
private fun switchProblemsOf(op: DialogOp, before: DialogView, after: DialogView, lastShown: LastShown?, expectNotice: GenerateDialogListNotice?): List<String> {
    val problems = mutableListOf<String>()
    val b = before.state
    val a = after.state
    val moved = after.selectionMovedFrom(before)
    val target = when (op) {
        is DialogOp.SelectTemplate -> b.candidates.getOrNull(op.pick % b.candidates.size.coerceAtLeast(1))?.id
        is DialogOp.SelectDefinition -> b.definitions.getOrNull(op.pick % b.definitions.size.coerceAtLeast(1))
            ?.takeIf { it.id != b.selectedTemplate.module }
            ?.let { d -> after.usable.first { it.module.id == d.id }.id }
        else -> null
    }
    if (target != null && a.selectedTemplate != target) problems += "$op selected ${a.selectedTemplate.template}, not ${target.template}"
    if (!moved) {
        if (a.fields.map { it.name to it.value } != b.fields.map { it.name to it.value }) problems += "$op kept the template but changed the inputs"
        return problems
    }
    val next = a.selected ?: return problems + "$op left no template selected"
    val expected = expectedCarried(lastShown?.template, lastShown?.values.orEmpty(), next, after.index.seedsFor(after.origin, next.id))
    val actual = a.fields.associate { it.name to it.value }
    if (actual != expected) problems += "after $op the fields read $actual, but the carried values are $expected"
    if (a.listNotice != expectNotice) problems += "notice ${a.listNotice} != $expectNotice"
    return problems
}

/** The shared list changed while the dialog was open. */
private fun listChangeProblemsOf(before: DialogView, after: DialogView, lastShown: LastShown?): List<String> {
    val problems = mutableListOf<String>()
    val b = before.state
    val a = after.state
    val usable = after.usable
    val kept = usable.any { it.id == b.selectedTemplate }
    val sameDefinition = usable.firstOrNull { it.module.id == b.selectedTemplate.module }
    when {
        kept -> {
            if (a.selectedTemplate != b.selectedTemplate) problems += "the list still has ${b.selectedTemplate.template} but the selection moved to ${a.selectedTemplate.template}"
            if (b.selected != null && a.fields.map { it.name to it.value } != b.fields.map { it.name to it.value }) problems += "the selected template stayed but its inputs changed"
        }
        sameDefinition != null -> {
            if (a.selectedTemplate != sameDefinition.id) problems += "gone: selected ${a.selectedTemplate.template}, not the first of its definition ${sameDefinition.id.template}"
            val notice = a.listNotice
            if (notice !is GenerateDialogListNotice.TemplateReplaced) problems += "gone but the notice is $notice"
            if (notice is GenerateDialogListNotice.TemplateReplaced && lastShown != null && notice.goneTitle != lastShown.template.template.title) problems += "the notice names '${notice.goneTitle}', not '${lastShown.template.template.title}'"
            val next = a.selected
            if (next != null) {
                val expected = expectedCarried(lastShown?.template, lastShown?.values.orEmpty(), next, after.index.seedsFor(after.origin, next.id))
                if (a.fields.associate { it.name to it.value } != expected) problems += "replaced: the fields read ${a.fields.associate { it.name to it.value }}, but the carried values are $expected"
            }
        }
        else -> {
            if (a.candidates.isNotEmpty()) problems += "no template of the definition is left but ${a.candidates.size} candidates show"
            if (a.listNotice != GenerateDialogListNotice.NoCandidates) problems += "no candidates but the notice is ${a.listNotice}"
            if (a.canGenerate) problems += "Generate can be pressed with no template"
        }
    }
    return problems
}

/** [Generate]: the request is the selected template alone, exactly when it can be pressed. */
internal fun generationProblemsOf(view: DialogView, request: SingleFileGenerationRequest?, again: SingleFileGenerationRequest?): List<String> {
    val problems = mutableListOf<String>()
    val s = view.state
    if (request != again) problems += "asking twice gives two requests"
    val canPress = s.canGenerate && s.selected?.template?.detail != null
    if ((request != null) != canPress) problems += "request ${if (request == null) "null" else "given"} while canGenerate=${s.canGenerate}"
    if (request == null) return problems
    if (request.template.id != s.selectedTemplate) problems += "the request is for ${request.template.id.template}, not the selected ${s.selectedTemplate.template}"
    if (request.origin != view.origin) problems += "the request lost its origin"
    if (request.target != s.target) problems += "the request writes ${request.target}, the dialog says ${s.target}"
    val names = request.args.map { it.first }
    if (names.toSet().size != names.size) problems += "an argument twice: $names"
    if ("template" in names || "onExisting" in names) problems += "the request carries the runner's own arguments: $names"
    val shown = s.fields.filter { it.slot is FieldSlot.Shown }.map { it.name }.toSet()
    val strays = names.filter { it !in shown }
    if (strays.isNotEmpty()) problems += "arguments of fields that are not shown: $strays"
    val missing = s.selected?.template?.detail?.captures.orEmpty().map { it.name }.filter { it !in names }
    if (missing.isNotEmpty()) problems += "captures missing from the arguments: $missing"
    return problems
}
