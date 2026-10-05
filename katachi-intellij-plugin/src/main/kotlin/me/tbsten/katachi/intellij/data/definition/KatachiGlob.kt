package me.tbsten.katachi.intellij.data.definition

/**
 * A port of katachi's `Glob` (`me.tbsten.katachi.dsl.internal.Glob`): the same syntax, compiled to
 * the same regular expression, so that the IDE answers "which entry holds this file" the way the
 * check does. `glob-cases.json` (generated from katachi's own `Glob`) pins the two together.
 *
 * | syntax | meaning |
 * |---|---|
 * | `*`  | exactly one level, one character or more. Never crosses the separator |
 * | `**` | zero levels or more. Only valid as a whole segment |
 *
 * Nothing else is a metacharacter: `{a,b}`, `?` and `[abc]` are rejected, and `\` escapes any of
 * `* \ { } ? [ ] ,`. Matching is case sensitive. Only what the IDE needs is ported: `matches`,
 * `literalPrefix` and `hasWildcard`; katachi's captured `wildcards` are not.
 *
 * ```kotlin
 * val glob = KatachiGlob.compile("core/domain/Get*UseCase.kt")
 * glob.matches("core/domain/GetUserUseCase.kt") // true
 * ```
 */
internal class KatachiGlob private constructor(
    /** The pattern as written. Two patterns are the same only when these strings and the separators are equal. */
    val pattern: String,
    /** `/` for file paths, `:` for module paths. */
    val separator: Char,
    private val regex: Regex,
    private val wildcardCount: Int,
    /**
     * The levels before the first one holding a wildcard, with their escapes undone: what any path
     * this pattern matches has to start with, level for level. Empty for a rooted pattern.
     */
    val literalPrefix: List<String>,
) {
    /** Whether the pattern contains a `*` or a `**`. */
    val hasWildcard: Boolean get() = wildcardCount > 0

    /** Whether [path] matches in full. */
    fun matches(path: String): Boolean = regex.matches(path)

    override fun equals(other: Any?): Boolean =
        other is KatachiGlob && other.pattern == pattern && other.separator == separator

    override fun hashCode(): Int = 31 * pattern.hashCode() + separator.hashCode()

    override fun toString(): String = "KatachiGlob($pattern)"

    companion object {
        const val PATH_SEPARATOR: Char = '/'
        const val MODULE_SEPARATOR: Char = ':'

        private const val DOUBLE_STAR: String = "**"
        private const val REJECTED: String = "{}?[]"
        private const val REGEX_METACHARACTERS: String = "\\.[]{}()*+-?^$|&/<>"

        /**
         * Translates [pattern] into a regular expression.
         *
         * @throws KatachiGlobSyntaxException when the pattern is empty, has an empty segment, uses
         *   `**` inside a larger segment, or uses a metacharacter katachi does not have.
         */
        fun compile(pattern: String, separator: Char = PATH_SEPARATOR): KatachiGlob {
            if (pattern.isEmpty()) throw KatachiGlobSyntaxException(pattern, KatachiGlobProblem.EmptyPattern)

            // A leading separator is the pattern being rooted (`:feature:*`), not an empty first segment.
            val leadingSeparator = pattern[0] == separator
            val body = if (leadingSeparator) pattern.substring(1) else pattern
            val segments = body.split(separator)
            if (segments.any { it.isEmpty() }) {
                throw KatachiGlobSyntaxException(pattern, KatachiGlobProblem.EmptySegment(separator))
            }

            val escapedSeparator = escapeForRegex(separator)
            val oneLevel = "[^$escapedSeparator]+"
            val manyLevels = "($oneLevel(?:$escapedSeparator$oneLevel)*)"
            val wildcards = Counter()
            val expression = StringBuilder()
            if (leadingSeparator) expression.append(escapedSeparator)

            segments.forEachIndexed { index, segment ->
                // A preceding `**` has already emitted the separator that follows it, inside its own
                // optional group, so that `a/**/b` still matches `a/b`.
                val needsSeparator = index > 0 && segments[index - 1] != DOUBLE_STAR
                if (segment == DOUBLE_STAR) {
                    wildcards.count++
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
                    expression.append(segmentExpression(pattern, segment, escapedSeparator, wildcards))
                }
            }

            return KatachiGlob(
                pattern = pattern,
                separator = separator,
                regex = Regex(expression.toString()),
                wildcardCount = wildcards.count,
                literalPrefix = if (leadingSeparator) emptyList() else literalPrefixOf(segments),
            )
        }

        private class Counter {
            var count: Int = 0
        }

        private fun literalPrefixOf(segments: List<String>): List<String> {
            val prefix = mutableListOf<String>()
            for (segment in segments) {
                prefix += literalOrNull(segment) ?: break
            }
            return prefix
        }

        /** [segment] with its escapes undone, or `null` when it holds a wildcard. */
        private fun literalOrNull(segment: String): String? {
            val literal = StringBuilder()
            var index = 0
            while (index < segment.length) {
                val character = segment[index]
                when (character) {
                    // `compile` has already rejected a trailing or unknown escape.
                    '\\' -> {
                        literal.append(segment[index + 1])
                        index += 2
                    }

                    '*' -> return null
                    else -> {
                        literal.append(character)
                        index++
                    }
                }
            }
            return literal.toString()
        }

        private fun escapeForRegex(character: Char): String =
            if (character in REGEX_METACHARACTERS) "\\$character" else character.toString()

        private fun segmentExpression(
            pattern: String,
            segment: String,
            escapedSeparator: String,
            wildcards: Counter,
        ): String {
            val expression = StringBuilder()
            var index = 0
            while (index < segment.length) {
                val character = segment[index]
                when {
                    character == '\\' -> {
                        val escaped = segment.getOrNull(index + 1)
                            ?: throw KatachiGlobSyntaxException(pattern, KatachiGlobProblem.TrailingBackslash)
                        if (escaped !in GLOB_ESCAPABLE) {
                            throw KatachiGlobSyntaxException(pattern, KatachiGlobProblem.UnescapableCharacter(escaped))
                        }
                        expression.append(escapeForRegex(escaped))
                        index += 2
                    }

                    character == '*' -> {
                        if (segment.getOrNull(index + 1) == '*') {
                            throw KatachiGlobSyntaxException(pattern, KatachiGlobProblem.DoubleStarInsideSegment(segment))
                        }
                        // One or more characters, never crossing a separator.
                        expression.append("([^$escapedSeparator]+)")
                        wildcards.count++
                        index++
                    }

                    character in REJECTED -> throw KatachiGlobSyntaxException(
                        pattern,
                        KatachiGlobProblem.RejectedMetacharacter(character),
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
