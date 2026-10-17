package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.presentation.gutter.RoleFileCount

/**
 * The words the definition side says about how many files a layout line holds (ide-authoring design 4.3): the Code Vision at the end of
 * `"*Service".ktFile()` and, through [filesText], the tooltip of a role's ◆. Every count is [RoleFileCount]'s, the one rule of the
 * plugin, so this never counts anything itself. English only, like the other Code Visions ("N usages").
 *
 * | The line | Text |
 * |---|---|
 * | 3 files | `3 files` (`1 file`) |
 * | none, optional | `no files` |
 * | none, `required` | `missing (required)` |
 * | the JSON cut the list | `200+ files` (the files listed, a lower bound) |
 * | the walk could not run | `? files` |
 *
 * A wildcard module key (one declaration, an entry per module) adds ` (4 modules)`; a count that may be older than the source adds
 * ` (stale)` last.
 *
 * ```kotlin
 * CountText.of(entries, stale = false, modules = 1)   // "3 files"
 * CountText.of(entries, stale = true, modules = 4)    // "3 files (4 modules) (stale)"
 * ```
 */
internal object CountText {
    /** The text of the line that declares [entries] (more than one when a wildcard module key turned the declaration into several). */
    fun of(entries: List<LayoutEntryModel>, stale: Boolean, modules: Int = 1): String {
        val count = RoleFileCount.of(entries)
        val base = when {
            count == null -> filesText(null)
            count == 0 -> if (entries.any { it.required }) MISSING else filesText(0)
            RoleFileCount.isCut(entries) -> "${RoleFileCount.listed(entries)}+ files"
            else -> filesText(count)
        }
        return base + (if (modules > 1) " ($modules modules)" else "") + (if (stale) " (stale)" else "")
    }

    /** `no files`, `1 file`, `3 files`, and `? files` for a count nobody knows. */
    fun filesText(count: Int?): String = when (count) {
        null -> "? files"
        0 -> "no files"
        1 -> "1 file"
        else -> "$count files"
    }

    private const val MISSING = "missing (required)"
}
