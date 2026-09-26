package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Description
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.internal.MetadataValues
import me.tbsten.katachi.dsl.Title

/**
 * What separates two sections of a generated page.
 *
 * Every section appends its own leading break, so a section that decides it has nothing to say
 * leaves no blank line behind it.
 */
internal const val SECTION_BREAK: String = "\n\n"

/**
 * What the documentation root is called when nothing else says.
 *
 * Kept as the last fallback rather than as the heading itself: `architecture { title = ... }` is
 * what names the root, and this is what a definition that never said gets.
 */
internal const val ROOT_TITLE: String = "Architecture"

/** What the root's heading adds to its name, so that the page says what it is. */
internal const val ROOT_TITLE_SUFFIX: String = " documentation"

/**
 * What the index of every generated page is written under.
 *
 * Like every heading katachi writes itself, it is in English. The pages are written in one
 * language rather than in the language of whoever reads them; what a definition writes --
 * `title`, `summary`, `description`, the heading of a `documentSection` -- stays as written.
 */
internal const val DOCUMENT_MAP_HEADING: String = "## Document map"

/**
 * What separates a link from the one line saying what it is.
 *
 * The same three dots a role page writes its `## Examples` with. One generated document should not
 * spell one idea two ways, and a dash is also a character a `summary` may open with.
 */
internal const val SUMMARY_SEPARATOR: String = " ... "

/** One level of a nested Markdown list. */
internal const val BULLET_INDENT: String = "  "

/** What a group's placement tree is written under. */
internal const val GROUP_PLACEMENT_HEADING: String = "## Placement in this group"

/**
 * What the roles written straight into `architecture { }` are listed under.
 *
 * They need a section of their own because the map above names them without their `summary`,
 * and every role that belongs to a group gets one in that group's section. Without this they
 * would be the only roles whose one line of prose appears nowhere on the page a reader starts on.
 */
internal const val ROOT_ROLES_HEADING: String = "## Roles at the root"

/**
 * What the root's placement tree is written under.
 *
 * Worded apart from [GROUP_PLACEMENT_HEADING] because the root is not a group -- it is where
 * the groups are -- and the tree there is of the roles written beside them, not of everything
 * below.
 */
internal const val ROOT_PLACEMENT_HEADING: String = "## Placement at the root"

/** The file every container of the generated tree is read through. */
internal const val README: String = "README.md"

/** The extension every generated page carries. */
internal const val PAGE_EXTENSION: String = ".md"

/**
 * The display name of a group: its `title` when one was written, its identifier otherwise.
 *
 * The fallback is the reader's rather than the DSL's -- see [Title] -- and this is the reader.
 */
internal val Group.displayName: String get() = this[Title] ?: name

/** The display name of a role. See [Group.displayName]. */
internal val Role.displayName: String get() = this[Title] ?: name

/**
 * Whether a group is written out at all.
 *
 * Absent means yes, and a group that opted out takes everything below it with it: its directory
 * is never created, so a page written inside it could not be reached.
 */
internal val Group.isDocumented: Boolean get() = this[Documented] ?: true

/** Whether a role gets a page of its own. See [Group.isDocumented]. */
internal val Role.isDocumented: Boolean get() = this[Documented] ?: true

/** A Markdown link, always relative to the page it is written on. */
internal fun link(text: String, path: String): String = "[${escapeLinkText(text)}](./$path)"

/**
 * A Markdown link to a page above the one it is written on.
 *
 * Kept apart from [link] because the target already opens with `../`, and `.././` would be the
 * same step spelled twice.
 */
private fun upwardLink(text: String, path: String): String = "[${escapeLinkText(text)}]($path)"

/** What separates two steps of a container page's breadcrumb. */
private const val BREADCRUMB_SEPARATOR: String = " / "

/** One step of a breadcrumb: what it reads as, and the way back to it from where it is written. */
internal class Crumb(
    /** The `title` of the container, or its identifier when it was given no title. */
    val text: String,
    /** Relative to the page the breadcrumb is written on, so it always opens with `../`. */
    val path: String,
)

/**
 * The way back out, above the heading.
 *
 * Only the containers a page is inside, never the page itself: a breadcrumb ending in the name
 * of the page a reader is already on says nothing they cannot see. The root has none at all,
 * which is what makes the line mean "there is something above this".
 *
 * Above the heading rather than below it because a reader who arrived at a nested group is
 * asking where they are before they read what is here, and because a line after the title
 * would sit between the title and the table it introduces.
 */
internal fun StringBuilder.appendBreadcrumb(ancestors: List<Crumb>) {
    if (ancestors.isEmpty()) return
    append(ancestors.joinToString(BREADCRUMB_SEPARATOR) { upwardLink(it.text, it.path) })
    append(SECTION_BREAK)
}

/**
 * The title of a page, and below it the one line `summary` that the listing above also shows.
 *
 * `summary` is a table cell, which is exactly why the DSL keeps it apart from `description`:
 * there a paragraph break would break the table, and here it is a paragraph like any other.
 *
 * One function for a role page and for a container page, because a group means by `summary` and
 * `description` what a role means: the two pages differ in what they list, not in how they
 * introduce themselves.
 */
internal fun StringBuilder.appendTitle(title: String, metadata: MetadataValues) {
    append("# $title")
    val summary = metadata[Summary] ?: return
    append(SECTION_BREAK)
    append(summary)
}

/**
 * The free-form body, exactly as it was written.
 *
 * Straight after the title, because it is the answer to "what is this" and a reader asks that
 * before asking what is inside. Nothing is done to the text -- no heading is shifted, no
 * paragraph is rewrapped -- because [Description] promises it is kept as written, and a promise
 * that holds everywhere but in the generated page is not a promise.
 */
internal fun StringBuilder.appendDescription(metadata: MetadataValues) {
    appendDescriptionText(metadata[Description])
}

/**
 * The same body, for a caller that has already chosen the text.
 *
 * The root's paragraph goes through the same blank-is-unwritten reading as its heading before it
 * gets here, so what is shared is the placing rather than the choosing.
 */
internal fun StringBuilder.appendDescriptionText(description: String?) {
    val text = description?.trim()
    if (text.isNullOrEmpty()) return
    append(SECTION_BREAK)
    append(text)
}

/**
 * One row of a Markdown table.
 *
 * Every cell goes through [escapeCell], because a table is the one place in the generated text
 * where a character a user wrote can change the structure of the page.
 */
internal fun tableRow(cells: List<String>): String =
    cells.joinToString(separator = " | ", prefix = "| ", postfix = " |") { escapeCell(it) }

/**
 * A value written by a user, made safe to put in a table cell.
 *
 * A `|` would end the cell and a newline would end the row, so both are folded away. `summary`
 * is one line by contract, but a contract is not a guarantee, and a broken table is far harder
 * to read than an escaped pipe.
 */
private fun escapeCell(value: String): String = oneLine(value.replace("|", "\\|"))

/**
 * A value written by a user, folded onto the one line the structure around it allows.
 *
 * A table row and a list item both end at the first newline, so a `summary` that turned out to
 * hold two lines would take the rest of the page with it.
 */
internal fun oneLine(value: String): String = value.lines().joinToString(" ") { it.trim() }.trim()

/** A `]` inside the text of a link would close it early. */
private fun escapeLinkText(text: String): String = text.replace("]", "\\]")

/**
 * ` at File.kt:42`, or nothing at all.
 *
 * The documentation root is produced by no line of any definition, so there is nothing to point
 * a reader at -- and `at <unknown>:-1` would send them looking for one.
 */
internal fun writtenAt(site: DeclarationSite): String =
    if (site == DeclarationSite.Unknown) "" else " at $site"
