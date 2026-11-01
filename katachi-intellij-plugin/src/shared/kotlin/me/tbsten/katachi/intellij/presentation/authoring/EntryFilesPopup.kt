package me.tbsten.katachi.intellij.presentation.authoring

import java.nio.file.Path

/** How many files the popup of a Code Vision lists before it says how many more there are. */
internal const val ENTRY_POPUP_FILE_LIMIT: Int = 200

/** How many files of a glob's directory the popup lists when the glob matched none. */
internal const val ENTRY_POPUP_DIRECTORY_LIMIT: Int = 50

/** What the popup of one Code Vision is made from, captured when the lens was drawn. */
internal data class EntryFilesInput(
    /** The resolved globs of the line: one, or one per module for a wildcard module key. */
    val patterns: List<String>,
    /** The distinct files the line holds, relative to [projectRoot], in the order the walk listed them (capped by the JSON). */
    val files: List<String>,
    /** How many files it holds ([RoleFileCount]'s); `null` when the walk could not run. */
    val count: Int?,
    val projectRoot: Path,
)

/** One line of the popup; a row with a [target] is a file that opens when it is chosen. */
internal data class EntryPopupRow(val text: String, val trailing: String? = null, val target: Path? = null) {
    /** Only a row with a file can be chosen; a heading ("— No file matches ...") and "... and N more" are text to read. */
    val selectable: Boolean get() = target != null
}

/** The popup a Code Vision opens: a [title] over [rows]. */
internal data class EntryFilesPopupModel(val title: String, val rows: List<EntryPopupRow>)

/**
 * The popup of the Code Vision on a layout line (ide-authoring design 4.3): the files the line holds, and for a line that holds none
 * the glob and the files that are really in its directory, which is how a typo in the glob shows. Without any IDE type; the files of
 * a directory come in as a function, since reading them is the IDE side's.
 *
 * ```kotlin
 * val popup = EntryFilesPopups.of(input, strings) { directory, limit -> namesIn(directory).take(limit) }
 * ```
 */
internal object EntryFilesPopups {
    /** [directoryFiles] gives the names of the files directly in a directory (relative to the project root, `""` for the root itself), at most `limit` of them. */
    fun of(input: EntryFilesInput, strings: AuthoringStrings, directoryFiles: (directory: String, limit: Int) -> List<String>): EntryFilesPopupModel = when {
        input.count == null -> EntryFilesPopupModel(title(input), listOf(EntryPopupRow(strings.countUnknown)))
        input.files.isNotEmpty() -> EntryFilesPopupModel(title(input), filesRows(input, strings))
        else -> EntryFilesPopupModel(title(input), noFileRows(input, strings, directoryFiles))
    }

    /** The directory a glob is looked for in: the segments before the first one with a wildcard, never the last (the file name). `""` is the project root. */
    fun directoryOf(pattern: String): String =
        pattern.split('/').dropLast(1).takeWhile { segment -> segment.none { it in GLOB_CHARACTERS } }.joinToString("/")

    // `layout` is the DSL's word for the block, not an English one: the same in every language.
    private fun title(input: EntryFilesInput): String = input.patterns.distinct().joinToString(", ").ifEmpty { "layout" }

    private fun filesRows(input: EntryFilesInput, strings: AuthoringStrings): List<EntryPopupRow> {
        val shown = input.files.take(ENTRY_POPUP_FILE_LIMIT)
        val rows = shown.map { path ->
            EntryPopupRow(path.substringAfterLast('/'), path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }, input.projectRoot.resolve(path))
        }
        val more = (input.count ?: shown.size) - shown.size
        return rows + if (more > 0) listOf(EntryPopupRow(strings.moreFiles(more))) else emptyList()
    }

    private fun noFileRows(input: EntryFilesInput, strings: AuthoringStrings, directoryFiles: (String, Int) -> List<String>): List<EntryPopupRow> = buildList {
        for (pattern in input.patterns.distinct()) {
            add(EntryPopupRow(strings.noFileMatches(pattern)))
            val directory = directoryOf(pattern)
            val named = directoryFiles(directory, ENTRY_POPUP_DIRECTORY_LIMIT + 1)
            val where = directory.ifEmpty { strings.projectRoot }
            if (named.isEmpty()) {
                add(EntryPopupRow(strings.noFileIn(where)))
                continue
            }
            add(EntryPopupRow(strings.filesIn(where)))
            named.take(ENTRY_POPUP_DIRECTORY_LIMIT).forEach { name ->
                add(EntryPopupRow(name, directory.takeIf { it.isNotEmpty() }, input.projectRoot.resolve(if (directory.isEmpty()) name else "$directory/$name")))
            }
            if (named.size > ENTRY_POPUP_DIRECTORY_LIMIT) add(EntryPopupRow(strings.andMore))
        }
    }

    private val GLOB_CHARACTERS = setOf('*', '?', '[', '{')
}
