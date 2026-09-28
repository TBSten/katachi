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
        // TODO(A1)
        return null
    }

    /** [relative] (a directory's path relative to the root) when it is a proper prefix of the pattern; `null` otherwise. */
    fun matchDirectory(relative: List<String>): SegmentMatch? {
        // TODO(A1)
        return null
    }

    companion object {
        /**
         * `null` for a pattern the index leaves out (spike S1 decision 6): one with an unnamed `*` / `**`,
         * or an empty one.
         */
        fun parse(text: String): PathPattern? {
            // TODO(A1)
            return null
        }
    }
}
