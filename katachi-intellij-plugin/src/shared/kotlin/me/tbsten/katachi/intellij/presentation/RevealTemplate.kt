package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.TemplateId

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
