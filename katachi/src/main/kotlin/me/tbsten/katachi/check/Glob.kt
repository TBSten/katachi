package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.InternalKatachiApi

/** A glob pattern katachi cannot make sense of. */
public class GlobSyntaxException internal constructor(
    message: String,
) : IllegalArgumentException(message)

/** What a pattern captured when it matched. */
@InternalKatachiApi
public class GlobMatch internal constructor(
    /**
     * One element per `*`, and one element **per level** for a `**`, in the order the
     * wildcards appear in the pattern. A `**` that matched no level contributes nothing, so
     * `":feature:**"` against `:feature` captures an empty list.
     */
    public val wildcards: List<String>,
) {
    override fun toString(): String = "GlobMatch($wildcards)"
}

internal enum class GlobGroupKind { Single, Recursive }

/**
 * katachi's glob, translated to a regular expression.
 *
 * | syntax | meaning |
 * |---|---|
 * | `*`  | exactly one level, one character or more. Never crosses [separator] |
 * | `**` | zero levels or more. Only valid as a whole segment |
 *
 * Nothing else is a metacharacter. `{a,b}`, `?` and `[abc]` are **rejected** rather than
 * silently treated as literals, because someone who writes them is expecting another tool's
 * glob and should hear about it. Prefix any of `* \ { } ? [ ] ,` with `\` to mean it
 * literally.
 *
 * The same class matches module paths (`:` separated) and file paths (`/` separated): the
 * separator is the only difference, so `*` and `**` cannot come to mean two different things.
 *
 * Matching is case sensitive on every platform, including a case insensitive macOS volume,
 * so that a check gives the same answer locally and on CI.
 *
 * The JDK's `PathMatcher` is not used: it answers yes or no, and katachi has to hand the
 * matched parts back to the user as `wildcards`.
 */
@InternalKatachiApi
public class Glob private constructor(
    /** The pattern as written. Two patterns are the same only when these strings are equal. */
    public val pattern: String,
    /** `/` for file paths, `:` for module paths. */
    public val separator: Char,
    private val segments: List<String>,
    private val regex: Regex,
    private val groupKinds: List<GlobGroupKind>,
) {
    /** Whether the pattern contains a `*` or a `**`. Such a declaration is optional by nature. */
    public val hasWildcard: Boolean get() = groupKinds.isNotEmpty()

    /** Whether [path] matches in full. */
    public fun matches(path: String): Boolean = regex.matches(path)

    /** The match and what it captured, or `null` when [path] does not match. */
    public fun match(path: String): GlobMatch? {
        val result = regex.matchEntire(path) ?: return null
        val wildcards = mutableListOf<String>()
        groupKinds.forEachIndexed { index, kind ->
            val captured = result.groupValues.getOrElse(index + 1) { "" }
            when (kind) {
                GlobGroupKind.Single -> wildcards += captured
                // A `**` that matched nothing leaves an empty group; it contributes no level.
                GlobGroupKind.Recursive -> if (captured.isNotEmpty()) wildcards += captured.split(separator)
            }
        }
        return GlobMatch(wildcards)
    }

    /**
     * Rejects a pattern whose `**` is anywhere but at the end, or that has more than one.
     *
     * Module paths call this. Flattening `**` level by level means every level it matches
     * lands at the end of `wildcards`, so a `**` in the middle would shift the index of the
     * `*` after it from one match to the next.
     */
    public fun requireAtMostOneTrailingDoubleStar() {
        val positions = segments.indices.filter { segments[it] == DOUBLE_STAR }
        if (positions.size > 1) {
            throw GlobSyntaxException(
                "`$pattern` uses `**` ${positions.size} times. A module path may use `**` at " +
                    "most once, as its last segment, so that the index of each captured " +
                    "wildcard is the same for every match.",
            )
        }
        val position = positions.firstOrNull() ?: return
        if (position != segments.lastIndex) {
            throw GlobSyntaxException(
                "`$pattern` uses `**` before its last segment. A module path may only use `**` " +
                    "as its last segment, so that the index of each captured wildcard is the " +
                    "same for every match.",
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        other is Glob && other.pattern == pattern && other.separator == separator

    override fun hashCode(): Int = 31 * pattern.hashCode() + separator.hashCode()

    override fun toString(): String = "Glob($pattern)"

    public companion object {
        /** Separator of a file path. */
        public const val PATH_SEPARATOR: Char = '/'

        /** Separator of a Gradle module path. */
        public const val MODULE_SEPARATOR: Char = ':'

        /**
         * Translates [pattern] into a regular expression.
         *
         * @throws GlobSyntaxException when the pattern is empty, has an empty segment, uses
         *   `**` as part of a larger segment, or uses a metacharacter katachi does not have.
         */
        public fun compile(pattern: String, separator: Char = PATH_SEPARATOR): Glob {
            if (pattern.isEmpty()) throw GlobSyntaxException("A glob pattern must not be empty.")

            // A leading separator is the pattern being rooted (`:feature:*`), not an empty
            // first segment.
            val leadingSeparator = pattern[0] == separator
            val body = if (leadingSeparator) pattern.substring(1) else pattern
            val segments = body.split(separator)
            if (segments.any { it.isEmpty() }) {
                throw GlobSyntaxException(
                    "`$pattern` has an empty segment. Two `$separator` in a row, or a trailing " +
                        "`$separator`, matches nothing.",
                )
            }

            val escapedSeparator = escapeForRegex(separator)
            val oneLevel = "[^$escapedSeparator]+"
            val manyLevels = "($oneLevel(?:$escapedSeparator$oneLevel)*)"
            val kinds = mutableListOf<GlobGroupKind>()
            val expression = StringBuilder()
            if (leadingSeparator) expression.append(escapedSeparator)

            segments.forEachIndexed { index, segment ->
                // A preceding `**` has already emitted the separator that follows it, inside
                // its own optional group, so that `a/**/b` still matches `a/b`.
                val needsSeparator = index > 0 && segments[index - 1] != DOUBLE_STAR
                if (segment == DOUBLE_STAR) {
                    kinds += GlobGroupKind.Recursive
                    if (index == segments.lastIndex) {
                        expression.append(
                            if (needsSeparator) "(?:$escapedSeparator$manyLevels)?" else "$manyLevels?",
                        )
                    } else {
                        if (needsSeparator) expression.append(escapedSeparator)
                        expression.append("(?:$manyLevels$escapedSeparator)?")
                    }
                } else {
                    if (needsSeparator) expression.append(escapedSeparator)
                    expression.append(segmentExpression(pattern, segment, escapedSeparator, kinds))
                }
            }

            return Glob(
                pattern = pattern,
                separator = separator,
                segments = segments,
                regex = Regex(expression.toString()),
                groupKinds = kinds.toList(),
            )
        }

        private const val DOUBLE_STAR: String = "**"

        /** Characters that mean themselves once written as `\<char>`. */
        private const val ESCAPABLE: String = "*\\{}?[],"

        /** Characters of another tool's glob, rejected so that they are never silently literal. */
        private const val REJECTED: String = "{}?[]"

        private const val REGEX_METACHARACTERS: String = "\\.[]{}()*+-?^$|&/<>"

        private fun escapeForRegex(character: Char): String =
            if (character in REGEX_METACHARACTERS) "\\$character" else character.toString()

        private fun segmentExpression(
            pattern: String,
            segment: String,
            escapedSeparator: String,
            kinds: MutableList<GlobGroupKind>,
        ): String {
            val expression = StringBuilder()
            var index = 0
            while (index < segment.length) {
                val character = segment[index]
                when {
                    character == '\\' -> {
                        val escaped = segment.getOrNull(index + 1) ?: throw GlobSyntaxException(
                            "`$pattern` ends a segment with `\\`. Write `\\\\` for a literal backslash.",
                        )
                        if (escaped !in ESCAPABLE) {
                            throw GlobSyntaxException(
                                "`$pattern` escapes `$escaped`, which katachi does not treat as a " +
                                    "metacharacter. Only `$ESCAPABLE` can be escaped.",
                            )
                        }
                        expression.append(escapeForRegex(escaped))
                        index += 2
                    }

                    character == '*' -> {
                        if (segment.getOrNull(index + 1) == '*') {
                            throw GlobSyntaxException(
                                "`$pattern` uses `**` as part of the segment `$segment`. `**` means " +
                                    "\"zero levels or more\" and only makes sense as a whole segment; " +
                                    "use a single `*` to match part of a name.",
                            )
                        }
                        // One or more characters, never crossing a separator. A `*` never
                        // captures the empty string, so `wildcards` never holds a blank.
                        expression.append("([^$escapedSeparator]+)")
                        kinds += GlobGroupKind.Single
                        index++
                    }

                    character in REJECTED -> throw GlobSyntaxException(
                        "`$pattern` uses `$character`. katachi's glob has only `*` and `**`; write " +
                            "`\\$character` for a literal `$character`, or write one layout key per " +
                            "alternative.",
                    )

                    else -> {
                        expression.append(escapeForRegex(character))
                        index++
                    }
                }
            }
            return expression.toString()
        }
    }
}
