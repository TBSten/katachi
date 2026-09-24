package me.tbsten.katachi.docs

import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Title

/**
 * What separates two sections of a generated page.
 *
 * Every section appends its own leading break, so a section that decides it has nothing to say
 * leaves no blank line behind it.
 */
internal const val SECTION_BREAK: String = "\n\n"

/** The heading of the documentation root, the one container with no name of its own. */
internal const val ROOT_TITLE: String = "アーキテクチャ"

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
internal fun escapeCell(value: String): String = value
    .replace("|", "\\|")
    .lines()
    .joinToString(" ") { it.trim() }
    .trim()

/** A `]` inside the text of a link would close it early. */
private fun escapeLinkText(text: String): String = text.replace("]", "\\]")
