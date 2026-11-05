package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.presentation.CandidateList
import java.io.IOException
import java.nio.file.Path

internal data class CaptureCandidateValues(val values: List<String>, val hiddenCount: Int)

internal object CaptureCandidates {
    const val LIMIT: Int = CandidateList.MAX

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

    fun levelsAbove(pattern: PathPattern, capture: String, entered: Map<String, String>): List<String>? {
        // The first level holding the capture; the same capture further in (kmp's second `feature`) is not used.
        val level = pattern.segments.indexOfFirst { segment -> segment.parts.any { it is PatternPart.Capture && it.name == capture } }
        if (level < 0 || level == pattern.segments.lastIndex) return null
        return (0 until level).map { concreteSegment(pattern.segments[it], entered) ?: return null }
    }

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
