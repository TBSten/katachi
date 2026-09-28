package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateModel

/** A row the search leaves on screen. */
internal data class SearchHit(
    val row: ModuleTemplate,
    /** Checked but not matching: shown faint as "outside search, selected", its form folded (E-16). */
    val isOutsideSearch: Boolean,
)

/**
 * The rows to show for [query]: a case-insensitive substring of the role name (simple or
 * qualified), title, summary, a parameter name or a capture name. A checked row always stays, so that nothing
 * invisible is generated.
 */
internal fun searchTemplates(rows: List<ModuleTemplate>, query: String, form: FormState): List<SearchHit> {
    val needle = query.trim()
    if (needle.isEmpty()) return rows.map { SearchHit(it, isOutsideSearch = false) }
    return rows.mapNotNull { row ->
        when {
            matches(row.template, needle) -> SearchHit(row, isOutsideSearch = false)
            form.isSelected(row.id) -> SearchHit(row, isOutsideSearch = true)
            else -> null
        }
    }
}

private fun matches(template: TemplateModel, needle: String): Boolean {
    val summary = template.summary
    val detailNames = template.detail?.let { detail -> detail.parameters.map { it.name } + detail.captures.map { it.name } }.orEmpty()
    val haystack = sequenceOf(summary.roleName, summary.title, summary.summary) +
        summary.parameterNames.asSequence() + summary.captureNames.asSequence() + detailNames.asSequence()
    return haystack.any { it != null && it.contains(needle, ignoreCase = true) }
}
