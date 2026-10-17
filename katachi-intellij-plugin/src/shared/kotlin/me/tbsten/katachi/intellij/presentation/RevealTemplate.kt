package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.allParametersOf

/**
 * [View template] of an editor notification (C2): highlights [id] and takes away what would hide
 * its row, so that the row is on screen once the list shows it. Its module band unfolds, and a
 * search that leaves the row out is cleared. The checks and inputs stay as they are: highlighting
 * is not checking.
 *
 * The list need not be loaded: the highlight waits in the state for the row to come, which is how
 * a tool window that is not open yet, or a list still loading, shows it later.
 *
 * ```kotlin
 * applyFormIntent(state, KatachiIntent.RevealTemplate(first.id)) // first of the index order (decision 9)
 * ```
 */
internal fun revealTemplate(state: KatachiScreenState, id: TemplateId): KatachiScreenState {
    val view = state.view
    val hidden = searchTemplates(state.rows, state.searchQuery, state.form).none { it.row.id == id && !it.isOutsideSearch }
    val clearsSearch = state.searchQuery.isNotBlank() && hidden
    return state.copy(
        searchQuery = if (clearsSearch) "" else state.searchQuery,
        view = view.copy(
            collapsedModules = view.collapsedModules - id.module,
            revealedRows = if (clearsSearch) emptySet() else view.revealedRows,
            highlight = Highlight(id, (view.highlight?.sequence ?: 0) + 1),
        ),
    )
}

/**
 * [revealTemplate] for a row the New menu picked (a template set): the row is checked and open, and [seeds] go into the
 * fields of it that are empty, through [inputField] so that fields of the same name in other checked rows follow (E-14).
 * A value already typed is not overwritten; a seed whose name is no field of the row is left out. While a generation runs
 * the form does not change; a finished generation's result is closed first ([leaveResult]), and a row that is not in the list (yet) or is unavailable is only revealed.
 *
 * ```kotlin
 * revealAndSeed(state, newFeature.id, mapOf("feature" to "home")) // checked, open, feature = home
 * ```
 */
internal fun revealAndSeed(state: KatachiScreenState, id: TemplateId, seeds: Map<String, String>): KatachiScreenState {
    val revealed = revealTemplate(state, id).let { if (it.generation is GenerationState.Finished) leaveResult(it, it.form) else it }
    val row = revealed.rows.firstOrNull { it.id == id }
    val detail = row?.template?.takeIf { it.isAvailable }?.detail
    if (revealed.generation != null || detail == null) return revealed
    val rows = revealed.rows
    var form = revealed.form
    if (!form.isSelected(id)) form = toggleCheck(form, rows, id)
    form = setExpanded(form, id, expanded = true)
    val names = allParametersOf(detail).map { it.name }.toSet()
    for ((name, value) in seeds) {
        val field = FieldId(id, name)
        if (name in names && value.isNotEmpty() && form.inputOf(field).isNullOrEmpty()) form = inputField(form, rows, field, value)
    }
    return revealed.copy(form = form)
}
