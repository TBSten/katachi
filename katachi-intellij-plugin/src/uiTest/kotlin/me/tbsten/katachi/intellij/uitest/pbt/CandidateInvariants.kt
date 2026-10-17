package me.tbsten.katachi.intellij.uitest.pbt

import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.presentation.BodyUi
import me.tbsten.katachi.intellij.presentation.FieldUi
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.KatachiUiState
import me.tbsten.katachi.intellij.presentation.ListItemUi
import me.tbsten.katachi.intellij.presentation.RowBodyUi
import me.tbsten.katachi.intellij.presentation.captureProblemOf
import java.nio.file.Path

/*
 * What the directory candidates of capture fields must be, whatever came before (ide-review-screens plan 4.2, invariants 1, 3, 6
 * and 8). Each check returns what it found wrong. They read the fields as drawn, against `CandidateReference` and the fake disk.
 */

/** A capture field on screen with what the reference needs to judge it. */
internal class CandidateFieldView(
    val field: FieldUi.Text,
    val capture: ParameterModel.CaptureParam,
    val pattern: String,
    val entered: Map<String, String>,
    val root: Path,
)

/** The capture fields of the open forms of [ui] (directory captures and module captures alike), with their patterns. */
internal fun candidateFieldsOf(state: KatachiScreenState, ui: KatachiUiState): List<CandidateFieldView> {
    val rows = state.rows.associateBy { it.id }
    val list = (ui.body as? BodyUi.Listing)?.list ?: return emptyList()
    return list.items.filterIsInstance<ListItemUi.Row>().flatMap { item ->
        val row = rows[item.row.id] ?: return@flatMap emptyList()
        val detail = row.template.detail ?: return@flatMap emptyList()
        val fields = (item.row.body as? RowBodyUi.Form)?.form?.fields.orEmpty().filterIsInstance<FieldUi.Text>()
        val captures = allParametersOf(detail).filterIsInstance<ParameterModel.CaptureParam>().associateBy { it.name }
        fields.mapNotNull { field ->
            val capture = captures[field.id.parameterName] ?: return@mapNotNull null
            val pattern = CandidateReference.patternHolding(detail.files.map { it.pattern }, capture.name) ?: return@mapNotNull null
            CandidateFieldView(field, capture, pattern, state.form.inputsOf(row.id), row.module.linkedRootPath)
        }
    }
}

/** The directories above the capture fields of every loaded template, with the root each lies under: where the tests add and remove directories. */
internal fun candidateParentsOf(state: KatachiScreenState): List<Pair<Path, Path>> = state.rows.flatMap { row ->
    val detail = row.template.detail ?: return@flatMap emptyList()
    val inputs = state.form.inputsOf(row.id)
    allParametersOf(detail).filterIsInstance<ParameterModel.CaptureParam>().mapNotNull { capture ->
        val pattern = CandidateReference.patternHolding(detail.files.map { it.pattern }, capture.name) ?: return@mapNotNull null
        CandidateReference.parentOf(pattern, capture.name, inputs, row.module.linkedRootPath)?.let { it to row.module.linkedRootPath }
    }
}.distinct().sortedBy { it.first.toString() }

/**
 * 1. At rest, the candidates a field offers are the reference's for the values entered now. 3. A directory that is gone is not
 * offered, and no name is offered that is no directory there (the lists are checked against the disk itself, not only the
 * reference). 6. A value that is none of the candidates says "new directory" and has no error (the other way round for a field
 * that lists modules, where it is the error); a candidate says nothing.
 */
internal fun candidateViolationsOf(state: KatachiScreenState, ui: KatachiUiState, disk: FakeDisk): List<String> =
    candidateFieldsOf(state, ui).flatMap { candidateFieldViolationsOf(it, disk) }

/** [candidateViolationsOf] for one field; the dialog's machine uses it for the selected template's fields. */
internal fun candidateFieldViolationsOf(view: CandidateFieldView, disk: FakeDisk): List<String> = buildList {
    run {
        val field = view.field
        val where = "${field.id.templateId.template}.${field.id.parameterName}"
        val offered = field.suggestions?.values.orEmpty()
        val modules = view.capture.existingModules
        if (modules == null) {
            val expected = CandidateReference.of(view.pattern, view.capture.name, view.entered, view.root, disk)
            if (expected != null && offered != expected.values) add("$where offers $offered, the reference says ${expected.values} (entered ${view.entered}, pattern ${view.pattern})")
            if (expected != null && (field.suggestions?.moreLabel != null) != (expected.hidden > 0)) add("$where: the \"more\" line ${field.suggestions?.moreLabel}, hidden by the reference ${expected.hidden}")
            val parent = CandidateReference.parentOf(view.pattern, view.capture.name, view.entered, view.root)
            val ghosts = if (parent == null) offered else offered.filter { name -> disk.childrenOf(parent).none { it.contains(name) } }
            if (ghosts.isNotEmpty()) add("$where offers $ghosts that are no directory under $parent")
        }
        val suggestions = field.suggestions ?: return@run
        val value = field.value
        if (modules != null) {
            if (suggestions.newDirectoryNote != null) add("$where lists modules and says \"new directory\"")
            if (value.isNotBlank() && modules.isNotEmpty() && value !in suggestions.values && field.error == null && suggestions.moreLabel == null) {
                add("$where: \"$value\" is no existing module and has no error")
            }
        } else {
            // A value that can be a directory name and is none of the candidates is no error: it is new (the note says so).
            val usable = value.isNotBlank() && captureProblemOf(value) == null
            if (usable && field.error != null) add("$where: \"$value\" can be a directory name and has the error ${field.error}")
            val newName = usable && field.error == null && suggestions.moreLabel == null && value !in suggestions.values
            if (newName != (suggestions.newDirectoryNote != null)) {
                add("$where: \"$value\" with candidates ${suggestions.values}, error ${field.error}: the new-directory note is ${suggestions.newDirectoryNote}")
            }
            if (value in suggestions.values && suggestions.newDirectoryNote != null) add("$where: \"$value\" is a candidate and still gets the note")
        }
    }
}

/** 8. Find Action's request reaches the state once per press, so that the list can put the focus in the search field. */
internal fun focusViolationsOf(before: KatachiScreenState, intents: List<KatachiIntent>, after: KatachiScreenState): List<String> {
    val presses = intents.count { it == KatachiIntent.FocusSearch }
    if (presses == 0) return emptyList()
    val now = after.view.focusRequest - before.view.focusRequest
    return if (now == presses) emptyList() else listOf("FocusSearch pressed $presses time(s) moved the focus request by $now")
}
