package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateId

/*
 * The list's keyboard rules (spec 04 "キーボード操作とフォーカス"), apart from Compose so that they
 * can be tested. Rows and their fields form one sequence; Tab follows the composition order, so only
 * the keys below need handling.
 */

/** Something in the list that can hold the focus. */
internal sealed interface FocusTarget {
    data object Search : FocusTarget

    /** The band of a definition module (two or more modules only, T-5). */
    data class ModuleHeader(val moduleId: ModuleId) : FocusTarget

    data class Row(val id: TemplateId) : FocusTarget

    data class Field(val id: FieldId) : FocusTarget
}

internal sealed interface NavKey {
    data object Up : NavKey

    data object Down : NavKey

    data object Space : NavKey

    data object Enter : NavKey

    data object Right : NavKey

    data object Left : NavKey

    data object Escape : NavKey

    /** ⌘⏎ / Ctrl+Enter. */
    data object Generate : NavKey

    /** A printable character typed on a row: speed search. */
    data class Type(val text: String) : NavKey
}

/** Where the focus goes after a key. */
internal sealed interface FocusMove {
    data class To(val target: FocusTarget) : FocusMove

    /**
     * The first empty required field of the row, else its first field. The form may only appear
     * after the intent is applied, so the UI resolves it once the row's fields are there.
     */
    data class FirstEmptyRequired(val id: TemplateId) : FocusMove
}

/** What a key does: an intent to send and where the focus moves; both `null` means "not handled". */
internal data class NavResult(val intent: KatachiIntent? = null, val focus: FocusMove? = null) {
    val isHandled: Boolean get() = intent != null || focus != null
}

/** The rows of [list] in order: the targets ↑ ↓ move between. */
internal fun rowsOf(list: ListUi): List<TemplateRowUi> = list.items.mapNotNull { (it as? ListItemUi.Row)?.row }

/** What ↑ ↓ visit in [list], in order: module bands and rows (group headers are not focusable). */
internal fun stopsOf(list: ListUi): List<FocusTarget> = list.items.mapNotNull { item ->
    when (item) {
        is ListItemUi.ModuleHeader -> FocusTarget.ModuleHeader(item.moduleId)
        is ListItemUi.Row -> FocusTarget.Row(item.row.id)
        is ListItemUi.GroupHeader -> null
    }
}

/** [field] of [form] to focus first: the first required one still empty, else the first editable one. */
internal fun firstEmptyRequiredOf(form: FormUi): FieldId? {
    val editable = form.fields.filter { it !is FieldUi.Collapsed }
    val empty = editable.firstOrNull { field ->
        field.isRequired && when (field) {
            is FieldUi.Text -> field.value.isBlank()
            is FieldUi.Choice -> field.selectedIndex < 0
            is FieldUi.Bool, is FieldUi.Collapsed -> false
        }
    }
    return (empty ?: editable.firstOrNull())?.id
}

/** What [key] does with the focus on [current]. */
internal fun navigate(list: ListUi, current: FocusTarget, key: NavKey): NavResult {
    if (key == NavKey.Generate) {
        val footer = list.footer
        return if (footer is FooterUi.Form && footer.generateEnabled) NavResult(intent = KatachiIntent.Generate) else NavResult()
    }
    val rows = rowsOf(list)
    val stops = stopsOf(list)
    return when (current) {
        FocusTarget.Search -> when (key) {
            NavKey.Down -> stops.firstOrNull()?.let { NavResult(focus = FocusMove.To(it)) } ?: NavResult()
            // Like the IDE's search fields: Esc clears the text, and does nothing when there is none (T-4).
            NavKey.Escape -> if (list.search.query.isNotEmpty()) NavResult(intent = KatachiIntent.Search("")) else NavResult()
            else -> NavResult()
        }
        is FocusTarget.ModuleHeader -> {
            val header = list.items.firstOrNull { it is ListItemUi.ModuleHeader && it.moduleId == current.moduleId } as? ListItemUi.ModuleHeader
                ?: return NavResult()
            onModuleHeader(header, stops.indexOf(current), stops, key)
        }
        is FocusTarget.Row -> {
            val index = rows.indexOfFirst { it.id == current.id }
            val row = rows.getOrNull(index) ?: return NavResult()
            onRow(row, stops.indexOf(current), stops, key, list.search.query)
        }
        is FocusTarget.Field -> when (key) {
            NavKey.Escape -> NavResult(focus = FocusMove.To(FocusTarget.Row(current.id.templateId)))
            else -> NavResult()
        }
    }
}

/** ↑ ↓ step through [stops]; the first stop's ↑ goes to the search field. */
private fun upDown(index: Int, stops: List<FocusTarget>, key: NavKey): NavResult? = when (key) {
    NavKey.Up -> NavResult(focus = FocusMove.To(stops.getOrNull(index - 1) ?: FocusTarget.Search))
    NavKey.Down -> stops.getOrNull(index + 1)?.let { NavResult(focus = FocusMove.To(it)) } ?: NavResult()
    else -> null
}

/** T-5: Enter / Space toggle the band, → opens it, ← folds it. */
private fun onModuleHeader(header: ListItemUi.ModuleHeader, index: Int, stops: List<FocusTarget>, key: NavKey): NavResult {
    upDown(index, stops, key)?.let { return it }
    val toggle = NavResult(intent = KatachiIntent.ToggleModule(header.moduleId))
    return when (key) {
        NavKey.Enter, NavKey.Space -> toggle
        NavKey.Right -> if (header.isCollapsed) toggle else NavResult()
        NavKey.Left -> if (header.isCollapsed) NavResult() else toggle
        else -> NavResult()
    }
}

private fun onRow(row: TemplateRowUi, index: Int, stops: List<FocusTarget>, key: NavKey, query: String): NavResult {
    upDown(index, stops, key)?.let { return it }
    val check = row.lead as? RowLeadUi.Check
    val checked = check?.checked == true
    val checkable = check?.enabled == true
    return when (key) {
        NavKey.Space -> if (checkable) NavResult(intent = KatachiIntent.ToggleCheck(row.id)) else NavResult()
        NavKey.Enter -> when {
            checked && row.isExpanded == false ->
                NavResult(KatachiIntent.SetExpanded(row.id, expanded = true), FocusMove.FirstEmptyRequired(row.id))
            checked -> NavResult(focus = FocusMove.FirstEmptyRequired(row.id))
            checkable -> NavResult(KatachiIntent.ToggleCheck(row.id), FocusMove.FirstEmptyRequired(row.id))
            else -> NavResult()
        }
        NavKey.Right -> if (checked && row.isExpanded == false) NavResult(intent = KatachiIntent.SetExpanded(row.id, expanded = true)) else NavResult()
        NavKey.Left -> if (checked && row.isExpanded == true) NavResult(intent = KatachiIntent.SetExpanded(row.id, expanded = false)) else NavResult()
        is NavKey.Type -> NavResult(KatachiIntent.Search(query + key.text), FocusMove.To(FocusTarget.Search))
        NavKey.Up, NavKey.Down, NavKey.Escape, NavKey.Generate -> NavResult()
    }
}
