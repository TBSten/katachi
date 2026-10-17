package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.presentation.CandidateList
import java.io.IOException
import java.nio.file.Path

/**
 * The directory names offered for a capture field: [values] (at most [CaptureCandidates.LIMIT]) and how many more
 * there were ([hiddenCount], shown as "{n} more").
 */
internal data class CaptureCandidateValues(val values: List<String>, val hiddenCount: Int)

/**
 * The candidates for a directory capture: the directories that exist at the capture's place in the template's file
 * pattern. For kmp's `feature/${feature}/src/…`, the capture `feature` gets `home` and `settings` from `<root>/feature/`.
 *
 * Pure: the disk is read through a [DirectoryLister]. The levels above the capture are made concrete from the values
 * already entered; when one cannot be (a value not entered yet, a conversion this plugin does not know, a glob
 * character), there are no candidates. Each directory is matched back with [PathPattern.matchDirectory], so a mixed
 * level such as `feature-${feature}` yields the captured part, and a name katachi would refuse yields nothing.
 *
 * ```kotlin
 * val pattern = PathPattern.parse("feature/\${feature}/src/\${name}.kt")!!
 * CaptureCandidates.of(pattern, "feature", emptyMap(), root, lister) // CaptureCandidateValues(["home", "settings"], 0)
 * ```
 */
internal object CaptureCandidates {
    const val LIMIT: Int = CandidateList.MAX

    /**
     * @param entered the values typed or chosen so far, by capture / parameter name.
     * @param root the definition's root (`placementRootOf`) the pattern is relative to.
     * @param onFailure called with the cause when the directory could not be read (the result is then `null`), so the caller can log it.
     * @return `null` when there are no candidates (nothing to offer, not computable, or unreadable); an empty list is never returned.
     */
    fun of(
        pattern: PathPattern,
        capture: String,
        entered: Map<String, String>,
        root: Path,
        lister: DirectoryLister,
        onFailure: (IOException) -> Unit = {},
    ): CaptureCandidateValues? {
        val above = levelsAbove(pattern, capture, entered) ?: return null
        val directory = above.fold(root) { path, name -> path.resolve(name) }
        val children = try {
            lister.childDirectories(directory)
        } catch (failure: IOException) {
            onFailure(failure)
            return null
        }
        val found = children.asSequence()
            .filterNot { it.startsWith(".") }
            .mapNotNull { child -> pattern.matchDirectory(above + child)?.decided?.get(capture) }
            .distinct()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            .toList()
        if (found.isEmpty()) return null
        return CaptureCandidateValues(found.take(LIMIT), (found.size - LIMIT).coerceAtLeast(0))
    }

    /**
     * The levels above the one holding [capture], made concrete from [entered]; `null` when there is nothing to offer
     * (no such capture, it sits in the file name, or a level is not known yet). Also the key of a request: the same
     * levels give the same candidates.
     */
    fun levelsAbove(pattern: PathPattern, capture: String, entered: Map<String, String>): List<String>? {
        // The first level holding the capture; the same capture further in (kmp's second `feature`) is not used.
        val level = pattern.segments.indexOfFirst { segment -> segment.parts.any { it is PatternPart.Capture && it.name == capture } }
        if (level < 0 || level == pattern.segments.lastIndex) return null
        return (0 until level).map { concreteSegment(pattern.segments[it], entered) ?: return null }
    }

    /**
     * Where a value of [capture] lands, for the note of a value that is no existing directory: the levels above, then its own
     * level with the capture written [CandidateList.VALUE_MARK] (`feature/{value}`). Other captures of the level that are
     * not entered stay `${name}`; derived parts stay `<name.transform>`.
     */
    fun directoryPatternOf(pattern: PathPattern, capture: String, entered: Map<String, String>): String? {
        val above = levelsAbove(pattern, capture, entered) ?: return null
        val own = pattern.segments[above.size].parts.joinToString("") { part ->
            when (part) {
                is PatternPart.Literal -> part.text
                is PatternPart.Capture ->
                    if (part.name == capture) CandidateList.VALUE_MARK else entered[part.name]?.takeIf { it.isNotBlank() } ?: "\${${part.name}}"
                is PatternPart.Derived -> "<${part.name}${part.transform?.let { ".$it" }.orEmpty()}>"
            }
        }
        return (above + own).joinToString("/")
    }

    /** The level as a directory name, or `null` when a part of it is not known yet. */
    private fun concreteSegment(segment: PatternSegment, entered: Map<String, String>): String? {
        val text = buildString {
            for (part in segment.parts) {
                when (part) {
                    is PatternPart.Literal -> {
                        if (part.text.any { it in GLOB_CHARACTERS }) return null
                        append(part.text)
                    }
                    is PatternPart.Capture -> append(entered[part.name]?.takeIf { it.isNotBlank() } ?: return null)
                    is PatternPart.Derived -> {
                        val source = entered[part.name]?.takeIf { it.isNotBlank() } ?: return null
                        val conversion = conversionOf(part.transform ?: return null) ?: return null
                        append(conversion(source))
                    }
                }
            }
        }
        // A value with a separator would reach into another directory.
        if (text.isEmpty() || text == "." || text == ".." || text.any { it == '/' || it == '\\' }) return null
        return text
    }

    private const val GLOB_CHARACTERS = "*?[{"
}
