package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel

/**
 * The other files of a role the menu lists (design 2.3, U-8): the ones in the same directory first, then the rest in the
 * order katachi walked them, at most [LIMIT]. [more] counts what is not listed, so the menu can say `… and 12 more`.
 *
 * The files are the `matchedFiles` of the role's file entries, as the definition task saw them: a file made after it
 * is not here. An entry whose list was cut ([LayoutEntryModel.truncated]) adds what it left out to [more].
 * TODO: when the file itself is among the cut ones, [more] counts it once too many.
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

        fun of(file: String, entries: List<LayoutEntryModel>, limit: Int = LIMIT): SiblingFiles {
            val holders = entries.filter { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }
            val others = holders.flatMap { it.matchedFiles }.distinct().filter { it != file }
            val directory = parentOf(file)
            val ordered = others.filter { parentOf(it) == directory } + others.filter { parentOf(it) != directory }
            val cutOff = holders.sumOf { entry ->
                if (entry.truncated) ((entry.matchedCount ?: entry.matchedFiles.size) - entry.matchedFiles.size).coerceAtLeast(0) else 0
            }
            return SiblingFiles(ordered.take(limit), (ordered.size - limit).coerceAtLeast(0) + cutOff)
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
