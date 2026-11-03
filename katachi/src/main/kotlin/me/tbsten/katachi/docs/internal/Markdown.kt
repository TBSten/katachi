package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Description
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.internal.MetadataValues
import me.tbsten.katachi.dsl.Title

// Every section appends its own leading break, so an empty section leaves no blank line behind.
internal const val SECTION_BREAK: String = "\n\n"

internal const val ROOT_TITLE: String = "Architecture"

internal const val ROOT_TITLE_SUFFIX: String = " documentation"

internal const val DOCUMENT_MAP_HEADING: String = "## Document map"

internal const val SUMMARY_SEPARATOR: String = " ... "

internal const val BULLET_INDENT: String = "  "

internal const val GROUP_PLACEMENT_HEADING: String = "## Placement in this group"

internal const val ROOT_ROLES_HEADING: String = "## Roles at the root"

internal const val ROOT_PLACEMENT_HEADING: String = "## Placement at the root"

internal const val README: String = "README.md"

internal const val PAGE_EXTENSION: String = ".md"

internal val Group.displayName: String get() = this[Title] ?: name

internal val Role.displayName: String get() = this[Title] ?: name

internal val Group.isDocumented: Boolean get() = this[Documented] ?: true

internal val Role.isDocumented: Boolean get() = this[Documented] ?: true

internal fun link(text: String, path: String): String = "[${escapeLinkText(text)}](./$path)"

private fun upwardLink(text: String, path: String): String = "[${escapeLinkText(text)}]($path)"

private const val BREADCRUMB_SEPARATOR: String = " / "

internal class Crumb(
    val text: String,
    val path: String,
)

internal fun StringBuilder.appendBreadcrumb(ancestors: List<Crumb>) {
    if (ancestors.isEmpty()) return
    append(ancestors.joinToString(BREADCRUMB_SEPARATOR) { upwardLink(it.text, it.path) })
    append(SECTION_BREAK)
}

internal fun StringBuilder.appendTitle(title: String, metadata: MetadataValues) {
    append("# $title")
    val summary = metadata[Summary] ?: return
    append(SECTION_BREAK)
    append(summary)
}

internal fun StringBuilder.appendDescription(metadata: MetadataValues) {
    appendDescriptionText(metadata[Description])
}

internal fun StringBuilder.appendDescriptionText(description: String?) {
    val text = description?.trim()
    if (text.isNullOrEmpty()) return
    append(SECTION_BREAK)
    append(text)
}

internal fun tableRow(cells: List<String>): String =
    cells.joinToString(separator = " | ", prefix = "| ", postfix = " |") { escapeCell(it) }

// A `|` ends the cell and a newline ends the row; both are folded away.
private fun escapeCell(value: String): String = oneLine(value.replace("|", "\\|"))

// Table rows and list items end at the first newline.
internal fun oneLine(value: String): String = value.lines().joinToString(" ") { it.trim() }.trim()

private fun escapeLinkText(text: String): String = text.replace("]", "\\]")

// The root has no declaration site; do not print `<unknown>:-1`.
internal fun writtenAt(site: DeclarationSite): String =
    if (site == DeclarationSite.Unknown) "" else " at $site"
