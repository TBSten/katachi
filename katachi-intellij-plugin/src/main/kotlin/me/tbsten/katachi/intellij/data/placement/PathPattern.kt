package me.tbsten.katachi.intellij.data.placement

internal sealed interface PatternPart {
    data class Literal(val text: String) : PatternPart

    data class Capture(val name: String) : PatternPart

    data class Derived(val name: String, val transform: String? = null) : PatternPart
}

internal data class PatternSegment(val parts: List<PatternPart>)

internal data class SegmentMatch(
    val decided: Map<String, String>,
    val undecided: List<String>,
    val remaining: List<PatternSegment>,
)

internal class PathPattern private constructor(
    val text: String,
    val segments: List<PatternSegment>,
) {
    fun matchFile(relative: List<String>): SegmentMatch? {
        if (relative.size != segments.size) return null
        val decided = matchSegments(relative) ?: return null
        return SegmentMatch(decided, undecided = emptyList(), remaining = emptyList())
    }

    fun matchDirectory(relative: List<String>): SegmentMatch? {
        if (relative.size >= segments.size) return null
        val decided = matchSegments(relative) ?: return null
        val remaining = segments.drop(relative.size)
        val undecided = remaining.flatMap { it.parts }.filterIsInstance<PatternPart.Capture>().map { it.name }
            .filter { it !in decided }.distinct()
        return SegmentMatch(decided, undecided, remaining)
    }

    private fun matchSegments(path: List<String>): Map<String, String>? {
        val decided = LinkedHashMap<String, String>()
        for ((index, text) in path.withIndex()) {
            val found = matchSegment(segments[index].parts, text, decided) ?: return null
            decided.putAll(found)
        }
        return decided
    }

    companion object {
        fun parse(text: String): PathPattern? {
            if (text.isEmpty()) return null
            val segments = text.split('/').map { segment ->
                if (segment.isEmpty() || '*' in segment) return null
                PatternSegment(partsOf(segment) ?: return null)
            }
            return PathPattern(text, segments)
        }
    }
}

private val PLACEHOLDER = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}|<([A-Za-z_][A-Za-z0-9_]*)(?:\.([A-Za-z_][A-Za-z0-9_]*))?>""")

private fun partsOf(segment: String): List<PatternPart>? {
    val parts = ArrayList<PatternPart>()
    var last = 0
    for (found in PLACEHOLDER.findAll(segment)) {
        if (found.range.first > last) parts += PatternPart.Literal(segment.substring(last, found.range.first))
        val capture = found.groups[1]?.value
        if (capture != null) {
            parts += PatternPart.Capture(capture)
        } else {
            val transform = found.groups[3]?.value
            if (transform != null && conversionOf(transform) == null) return null
            parts += PatternPart.Derived(found.groups[2]?.value.orEmpty(), transform)
        }
        last = found.range.last + 1
    }
    if (last < segment.length) parts += PatternPart.Literal(segment.substring(last))
    return parts
}

private fun matchSegment(parts: List<PatternPart>, text: String, known: Map<String, String>): Map<String, String>? {
    val hasCapture = parts.any { it is PatternPart.Capture }
    fun go(index: Int, position: Int, values: Map<String, String>): Map<String, String>? {
        if (index == parts.size) return if (position == text.length) values else null
        when (val part = parts[index]) {
            is PatternPart.Literal ->
                return if (text.startsWith(part.text, position)) go(index + 1, position + part.text.length, values) else null

            is PatternPart.Capture -> {
                for (end in position + 1..text.length) {
                    val value = text.substring(position, end)
                    if (captureValueProblem(value)) continue
                    go(index + 1, end, values + (part.name to value))?.let { return it }
                }
                return null
            }

            is PatternPart.Derived -> {
                val source = values[part.name] ?: known[part.name]
                if (source != null) {
                    // A named conversion is one spelling; a module key's `<x>` may be any of them.
                    val candidates = part.transform?.let { conversionOf(it) }?.let { listOf(it(source)) } ?: derivedForms(source)
                    for (candidate in candidates) {
                        if (!text.startsWith(candidate, position)) continue
                        go(index + 1, position + candidate.length, values)?.let { return it }
                    }
                    return null
                }
                val next = parts.getOrNull(index + 1)
                for (end in position + 1..text.length) {
                    if (next is PatternPart.Capture) {
                        go(index + 2, end, values + (next.name to ""))?.let { return it }
                    } else {
                        go(index + 1, end, values)?.let { return it }
                    }
                }
                return null
            }
        }
    }
    val result = go(0, 0, emptyMap()) ?: return null
    // A segment holding a capture is also checked as a whole, as katachi does with the filled-in segment.
    if (hasCapture && segmentProblem(text)) return null
    return result
}

internal fun derivedForms(source: String): List<String> {
    val words = nameWords(source)
    val capitalized = words.map { it.replaceFirstChar(Char::uppercaseChar) }
    val forms = listOf(
        source,
        capitalized.joinToString(""),
        words.mapIndexed { i, w -> if (i == 0) w else capitalized[i] }.joinToString(""),
        words.joinToString("-"),
        words.joinToString("_"),
        words.joinToString("_").uppercase(),
        words.joinToString(""),
        source.split('-').mapIndexed { i, w -> if (i == 0) w else w.replaceFirstChar(Char::uppercaseChar) }.joinToString(""),
        source.replace("-", ""),
    )
    return forms.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }
}

private const val UNCREATABLE = "*?[]{}:\"<>|"
private val WINDOWS_RESERVED = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }

private fun captureValueProblem(value: String): Boolean = when {
    value.isBlank() || value == "." || value == ".." -> true
    value.any { it == '/' || it == '\\' } -> true
    value.any { it in UNCREATABLE || Character.isISOControl(it) || isLineOrParagraphSeparator(it) } -> true
    value.first().isWhitespace() || value.last().isWhitespace() || value.last() == '.' -> true
    else -> value.substringBefore('.').uppercase() in WINDOWS_RESERVED
}

private fun isLineOrParagraphSeparator(character: Char): Boolean =
    Character.getType(character).let { it == Character.LINE_SEPARATOR.toInt() || it == Character.PARAGRAPH_SEPARATOR.toInt() }

private fun segmentProblem(segment: String): Boolean =
    segment.isBlank() || segment == "." || segment == ".." ||
        segment.first().isWhitespace() || segment.last().isWhitespace() || segment.last() == '.'
