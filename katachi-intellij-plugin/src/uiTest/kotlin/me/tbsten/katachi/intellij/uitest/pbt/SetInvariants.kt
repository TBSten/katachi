package me.tbsten.katachi.intellij.uitest.pbt

import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.BodyUi
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.KatachiUiState
import me.tbsten.katachi.intellij.presentation.FieldUi
import me.tbsten.katachi.intellij.presentation.ListItemUi
import me.tbsten.katachi.intellij.presentation.RowBodyUi
import me.tbsten.katachi.intellij.presentation.ExpectedLocation
import me.tbsten.katachi.intellij.presentation.expectedFilesOf

/*
 * What must hold of the template sets in the list, whatever came before (template-set plan 3.6, invariants (a) to (e)). Each
 * check returns what it found wrong, worded for the failure message.
 */

/**
 * (a) A run that has a set in it has nothing else in it, and (b) takes only the set's parameters beside `template` and
 * `onExisting`. [rows] are the list's rows of the module the run is for.
 */
internal fun setRunViolationOf(rows: List<ModuleTemplate>, specifiers: List<String>, args: Map<String, String>): String? {
    val sets = rows.filter { it.template.template in specifiers && it.template.set != null }
    if (sets.isEmpty()) return null
    if (specifiers.size != 1) return "a run of the template set ${sets.first().template.template} also holds $specifiers"
    val known = sets.single().template.detail?.parameters?.map { it.name }.orEmpty().toSet()
    val extra = args.keys - known - "template" - "onExisting"
    return if (extra.isEmpty()) null else "the run of the template set ${specifiers.single()} sent $extra, which are no parameter of the set ($known)"
}

/**
 * (c) The runs a generation of the checked [rows] is planned as: one for each set, and one for each module that has a template
 * checked (the module's templates share a build).
 */
internal fun plannedRunsOf(state: KatachiScreenState): Int {
    val checked = state.rows.filter { state.form.isSelected(it.id) && it.template.detail != null }
    return checked.count { it.template.set != null } + checked.filter { it.template.set == null }.map { it.module.id }.distinct().size
}

/** (c) The report of a generation that was planned as [planned] runs: that many items, a set alone in its own. */
internal fun reportViolationsOf(report: GenerationReport, planned: Int, state: KatachiScreenState): List<String> = buildList {
    if (report.items.size != planned) add("the generation was planned as $planned runs and reports ${report.items.size}: ${report.items.map { item -> item.templateIds.map { it.template } }}")
    val setIds = state.rows.filter { it.template.set != null }.map { it.id }.toSet()
    for (item in report.items) {
        if (item.templateIds.any { it in setIds } && item.templateIds.size != 1) add("a template set shares its run with ${item.templateIds.map { it.template }}")
    }
}

/**
 * (d) A set's row lists as many files as its members write, a file with a fixed path lands where the typed values put it (the
 * member's path with each `${p}` replaced by what was typed for `p`, worked out here from the members and the inputs, not by
 * the screen's own replacement), a file below a module capture has no fixed place, and (the screen) the row wears the badge
 * when it is a set and only then.
 */
internal fun setRowViolationsOf(state: KatachiScreenState, ui: KatachiUiState): List<String> = buildList {
    for (row in state.rows) {
        val set = row.template.set ?: continue
        val detail = row.template.detail ?: continue
        val inputs = state.form.inputsOf(row.id)
        val raw = detail.parameters.mapNotNull { p -> p.default?.let { p.name to it } }.toMap()
        // A default may read other parameters (one level): their typed text, else their plain default.
        val defaults = raw.mapValues { (_, default) ->
            Regex("""\$\{([^}]*)}""").replace(default) { m ->
                inputs[m.groupValues[1]]?.takeIf { it.isNotBlank() } ?: raw[m.groupValues[1]]?.takeIf { "\${" !in it } ?: m.value
            }
        }
        val expected = expectedFilesOf(detail, inputs)
        val written = set.members.flatMap { it.files }
        if (expected.size != written.size) add("the set ${row.template.template} lists ${expected.size} files, its members write ${written.size}")
        for ((file, shown) in written.zip(expected)) {
            val location = shown.location
            when {
                file.path != null -> {
                    val want = typedInto(file.path, inputs, defaults)
                    if (location != ExpectedLocation.Known(want)) add("the set ${row.template.template}: ${file.path} should land at $want, the screen says $location")
                }
                file.modulePlacement != null ->
                    if (location is ExpectedLocation.Known && !file.modulePlacement.modules.any { typedInto(it.path, inputs, defaults) == location.path }) {
                        add("the set ${row.template.template}: ${file.pattern} is placed at ${location.path}, which no module of its key gives")
                    }
            }
        }
    }
    val rows = state.rows.associateBy { it.id }
    val shown = (ui.body as? BodyUi.Listing)?.list?.items.orEmpty().filterIsInstance<ListItemUi.Row>()
    for (item in shown) {
        val isSet = rows[item.row.id]?.template?.set != null
        if (isSet != (item.row.set != null)) add("${item.row.id.template}: the set mark is ${item.row.set != null} for a ${if (isSet) "set" else "template"}")
        // Directory candidates only for a parameter that is a whole directory level of a fixed-path file; never one only a module key.
        val row = rows[item.row.id] ?: continue
        val detail = row.template.detail
        if (!isSet || detail == null) continue
        val slotNames = candidateSlotsOf(row, detail).map { it.first.name }.toSet()
        val fields = (item.row.body as? RowBodyUi.Form)?.form?.fields.orEmpty().filterIsInstance<FieldUi.Text>()
        for (field in fields.filter { it.suggestions != null && it.id.parameterName !in slotNames }) {
            add("${item.row.id.template}: ${field.id.parameterName} offers directories though it is no directory level of a file with a fixed path")
        }
        for (field in fields.filter { it.id.parameterName in slotNames && it.suggestions != null }) {
            if (field.isMultiline != null) add("${item.row.id.template}: ${field.id.parameterName} offers directories and still has the multi-line toggle")
        }
    }
}

/** [path] with each `${p}` replaced by the non-blank text typed for `p`, else its plain default (a placeholder with neither stays). */
private fun typedInto(path: String, inputs: Map<String, String>, defaults: Map<String, String>): String =
    Regex("""\$\{([^}]*)}""").replace(path) { match ->
        val name = match.groupValues[1]
        // The typed text as it was typed (the screen does not trim a value it uses), else the parameter's plain default.
        inputs[name]?.takeIf { it.isNotBlank() } ?: defaults[name] ?: match.value
    }

/**
 * (e) A checked set that a reload took out of the list is no longer checked, and is among [KatachiScreenState.removedTemplates]
 * (as a template is), unless its module went too.
 */
internal fun removedSetViolationsOf(before: KatachiScreenState, intents: List<KatachiIntent>, after: KatachiScreenState): List<String> = buildList {
    if (before.generation != null || after.generation != null) return@buildList
    val gone = before.rows.filter { row ->
        row.template.set != null && before.form.isSelected(row.id) && after.rows.none { it.id == row.id } &&
            after.snapshots.any { it.module.id == row.module.id }
    }
    for (row in gone) {
        if (after.form.isSelected(row.id)) add("the removed set ${row.template.template} is still checked")
        val explained = row.id in after.removedTemplates || intents.any { it == KatachiIntent.UncheckAll || it == KatachiIntent.DismissRemovedTemplates }
        if (!explained) add("the checked set ${row.template.template} left the list and is not among the removed templates ${after.removedTemplates.map(TemplateId::template)}")
    }
}

/** The kinds of set a step reached, for the coverage report. */
internal fun setReachedOf(state: KatachiScreenState, ui: KatachiUiState): List<String> = buildList {
    val sets = state.rows.filter { it.template.set != null }
    if (sets.isNotEmpty()) add("sets in the list")
    if (sets.any { it.template.detail == null }) add("set that cannot be checked")
    if (sets.any { state.form.isSelected(it.id) }) add("set checked")
    if (sets.any { it.id in state.form.expanded }) add("set form open")
    if (state.removedTemplates.any { id -> id.template.contains(".S") || id.template.startsWith("S") }) add("set removed by a reload")
    val shown = (ui.body as? BodyUi.Listing)?.list?.items.orEmpty().filterIsInstance<ListItemUi.Row>()
    if (shown.any { it.row.set != null }) add("set badge shown")
    val setFields = shown.filter { it.row.set != null }.flatMap { (it.row.body as? RowBodyUi.Form)?.form?.fields.orEmpty() }.filterIsInstance<FieldUi.Text>()
    if (setFields.any { it.suggestions != null }) add("set candidates shown")
}
