package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel

/**
 * The other files of a role the menu lists (design 2.3, U-8): the ones in the same directory first, then the rest in the
 * order katachi walked them, at most [LIMIT]. [more] counts what is not listed, so the menu can say `… and 12 more`.
 *
 * The files are the `matchedFiles` of the role's file entries, as the definition task saw them: a file made after it
 * is not here. An entry whose list was cut ([LayoutEntryModel.truncated]) adds what it left out to [more].
 * When the file is held by a cut entry (`holding`) and is not among its listed files, it is taken for one of the cut ones: [more] leaves it out,
 * as a file is not its own sibling. A file made after the definition task cannot be told from a cut one, and is taken for a cut one as well.
 * A file that no cut entry holds changes nothing, however many other entries were cut.
 *
 * ```kotlin
 * val siblings = SiblingFiles.of("src/main/kotlin/com/example/service/HealthService.kt", role.layout)
 * siblings.files // LegacyStatusService.kt's path first, the files of other directories after it
 * ```
 */
internal data class SiblingFiles(
    /** Relative to the project root, `/` separated, in the order to list them. */
    val files: List<String>,
    /** The files that exist but are not in [files]. */
    val more: Int,
) {
    companion object {
        const val LIMIT: Int = 15

        /**
         * [holding] is the entries of [entries] that the definition's answer for [file] names (`EntryMatch.entry` of the role); none when it is not known.
         */
        fun of(file: String, entries: List<LayoutEntryModel>, limit: Int = LIMIT, holding: List<LayoutEntryModel> = emptyList()): SiblingFiles {
            val holders = entries.filter { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }
            val others = holders.flatMap { it.matchedFiles }.distinct().filter { it != file }
            val directory = parentOf(file)
            val ordered = others.filter { parentOf(it) == directory } + others.filter { parentOf(it) != directory }
            val cutOff = holders.sumOf { entry ->
                if (entry.truncated) ((entry.matchedCount ?: entry.matchedFiles.size) - entry.matchedFiles.size).coerceAtLeast(0) else 0
            }
            // The entry that holds the file was cut and does not list it: it is among the cut ones, and a file is not its own sibling.
            val selfCut = cutOff > 0 && holding.any { it.truncated && file !in it.matchedFiles }
            return SiblingFiles(ordered.take(limit), (ordered.size - limit).coerceAtLeast(0) + cutOff - if (selfCut) 1 else 0)
        }

        /** `a/b` for `a/b/C.kt`; empty for a file at the project root. */
        fun parentOf(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")

        fun nameOf(path: String): String = path.substringAfterLast('/')

        /** [directory] cut in the middle with `…` when it is longer than [max]: `src/…/com/example/service`. A root file shows `.`. */
        fun shortDirectory(directory: String, max: Int = 40): String {
            if (directory.isEmpty()) return "."
            if (directory.length <= max) return directory
            val segments = directory.split('/')
            if (segments.size < 3) return directory
            val head = segments.first()
            var tail = listOf(segments.last())
            for (take in 2 until segments.size) {
                val candidate = segments.takeLast(take)
                if ((head + "/…/" + candidate.joinToString("/")).length > max) break
                tail = candidate
            }
            return head + "/…/" + tail.joinToString("/")
        }
    }
}
