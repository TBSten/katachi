package me.tbsten.katachi.intellij.data.placement

/** One piece of a pattern segment. */
internal sealed interface PatternPart {
    data class Literal(val text: String) : PatternPart

    /** `${name}`: a capture, one or more characters without `/`; a dialog field. */
    data class Capture(val name: String) : PatternPart

    /**
     * `<name>`: derived by katachi from the module capture `name` (`wildcard("x").pascalCase`,
     * `modulePackage`). Never a field. Matches one of the naming conversions of `name`'s value when
     * that is known, else any one or more characters (spike S1 §2).
     */
    data class Derived(val name: String) : PatternPart
}

/** One `/`-separated segment of a pattern. */
internal data class PatternSegment(val parts: List<PatternPart>)

/** What matching a path against a [PathPattern] found. */
internal data class SegmentMatch(
    /** Capture values of the matched segments, in pattern order. */
    val decided: Map<String, String>,
    /** Captures of the segments not reached, in pattern order. Empty for a file. */
    val undecided: List<String>,
    /** The segments not reached; empty for a file. */
    val remaining: List<PatternSegment>,
)

/**
 * A template's file pattern (`TemplateDetailModel.files[0].pattern`), relative to its definition's
 * project root and split into segments, and the reverse lookup the New menu and the notification
 * need (spike S1 §4).
 *
 * - A file matches when it has as many segments as the pattern and every segment matches; every
 *   capture is decided.
 * - A directory `d[0..k-1]` matches when `k` is less than the number of segments and its segments all
 *   match the pattern's first `k`; the captures of those are decided, the rest are not.
 * - Case-sensitive. Where a segment's split is ambiguous (`${a}-${b}`), the left capture takes the
 *   shortest value. A value katachi would refuse as a capture value does not match.
 *
 * ```kotlin
 * val pattern = PathPattern.parse("feature/\${feature}/ui/\${name}Screen.kt")
 * pattern?.matchDirectory(listOf("feature", "profile")) // decided feature=profile, undecided [name]
 * ```
 */
internal class PathPattern private constructor(
    /** The pattern as the JSON wrote it. */
    val text: String,
    val segments: List<PatternSegment>,
) {
    /** [relative] (the file's path relative to the root, split at `/`) when the whole pattern matches it; `null` otherwise. */
    fun matchFile(relative: List<String>): SegmentMatch? {
        if (relative.size != segments.size) return null
        val decided = matchSegments(relative) ?: return null
        return SegmentMatch(decided, undecided = emptyList(), remaining = emptyList())
    }

    /** [relative] (a directory's path relative to the root) when it is a proper prefix of the pattern; `null` otherwise. */
    fun matchDirectory(relative: List<String>): SegmentMatch? {
        if (relative.size >= segments.size) return null
        val decided = matchSegments(relative) ?: return null
        val remaining = segments.drop(relative.size)
        val undecided = remaining.flatMap { it.parts }.filterIsInstance<PatternPart.Capture>().map { it.name }
            .filter { it !in decided }.distinct()
        return SegmentMatch(decided, undecided, remaining)
    }

    /** The captures of the first `path.size` segments, or `null` when one of them does not match. */
    private fun matchSegments(path: List<String>): Map<String, String>? {
        val decided = LinkedHashMap<String, String>()
        for ((index, text) in path.withIndex()) {
            val found = matchSegment(segments[index].parts, text, decided) ?: return null
            decided.putAll(found)
        }
        return decided
    }

    companion object {
        /**
         * `null` for a pattern the index leaves out (spike S1 decision 6): one with an unnamed `*` / `**`,
         * or an empty one.
         */
        fun parse(text: String): PathPattern? {
            if (text.isEmpty()) return null
            val segments = text.split('/').map { segment ->
                if (segment.isEmpty() || '*' in segment) return null
                PatternSegment(partsOf(segment))
            }
            return PathPattern(text, segments)
        }
    }
}

private val PLACEHOLDER = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}|<([A-Za-z_][A-Za-z0-9_]*)>""")

private fun partsOf(segment: String): List<PatternPart> {
    val parts = ArrayList<PatternPart>()
    var last = 0
    for (found in PLACEHOLDER.findAll(segment)) {
        if (found.range.first > last) parts += PatternPart.Literal(segment.substring(last, found.range.first))
        val capture = found.groups[1]?.value
        parts += if (capture != null) PatternPart.Capture(capture) else PatternPart.Derived(found.groups[2]?.value.orEmpty())
        last = found.range.last + 1
    }
    if (last < segment.length) parts += PatternPart.Literal(segment.substring(last))
    return parts
}

/**
 * The captures of [text] matched against [parts], or `null`. [known] are the captures decided by the
 * segments above (a `<x>` needs the value of `x`). Captures take the shortest value that leaves the
 * rest matching; a `<x>` with an unknown `x` takes any one or more characters, and a capture right
 * after it is then left empty (the split is not decidable).
 */
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
                    for (candidate in derivedForms(source)) {
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

/** The spellings katachi can derive from the module capture value [source], longest first. */
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

/** katachi's `String.nameWords`: `-`, `_`, space end a word; a capital after a lower case letter or digit starts one. */
private fun nameWords(name: String): List<String> {
    val words = ArrayList<String>()
    val word = StringBuilder()
    fun endWord() {
        if (word.isNotEmpty()) {
            words += word.toString().lowercase()
            word.clear()
        }
    }
    name.forEachIndexed { index, character ->
        when {
            character == '-' || character == '_' || character == ' ' -> endWord()

            character.isUpperCase() -> {
                val previous = name.getOrNull(index - 1)
                val next = name.getOrNull(index + 1)
                val afterLowerOrDigit = previous != null && (previous.isLowerCase() || previous.isDigit())
                val lastOfCapitals = previous != null && previous.isUpperCase() && next != null && next.isLowerCase()
                if (afterLowerOrDigit || lastOfCapitals) endWord()
                word.append(character)
            }

            else -> word.append(character)
        }
    }
    endWord()
    return words
}

private const val UNCREATABLE = "*?[]{}:\"<>|"
private val WINDOWS_RESERVED = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }

/** Whether katachi would refuse [value] as a capture value (`captureValueProblemOf`). */
private fun captureValueProblem(value: String): Boolean = when {
    value.isBlank() || value == "." || value == ".." -> true
    value.any { it == '/' || it == '\\' } -> true
    value.any { it in UNCREATABLE || Character.isISOControl(it) || isLineOrParagraphSeparator(it) } -> true
    value.first().isWhitespace() || value.last().isWhitespace() || value.last() == '.' -> true
    else -> value.substringBefore('.').uppercase() in WINDOWS_RESERVED
}

private fun isLineOrParagraphSeparator(character: Char): Boolean =
    Character.getType(character).let { it == Character.LINE_SEPARATOR.toInt() || it == Character.PARAGRAPH_SEPARATOR.toInt() }

/** Whether the whole filled-in [segment] is one katachi would refuse (`filledSegmentProblemOf`). */
private fun segmentProblem(segment: String): Boolean =
    segment.isBlank() || segment == "." || segment == ".." ||
        segment.first().isWhitespace() || segment.last().isWhitespace() || segment.last() == '.'
