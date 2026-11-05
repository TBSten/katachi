package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException

internal class GlobMatch(
    // A `**` contributes one element per level it matched, and none when it matched no level.
    val wildcards: List<String>,
) {
    override fun toString(): String = "GlobMatch($wildcards)"
}

internal enum class GlobGroupKind { Single, Recursive }

/**
 * A compiled glob pattern, in the same syntax as a layout key.
 *
 * | syntax | meaning |
 * |---|---|
 * | `*`  | zero characters or more inside one level. Never crosses [separator]; captured |
 * | `**` | zero levels or more. Only valid as a whole segment; captured, one element per level |
 * | `?`  | one character other than [separator] |
 * | `[abc]` `[a-z]` `[!abc]` `[^abc]` | one character of the list or range, or not of it |
 * | `{a,b}` | one of the alternatives. A `*` inside is not captured |
 *
 * `?`, `[...]` and `{...}` never become a captured value. Prefix any of `* \ { } ? [ ] ,` with
 * `\` to mean it literally. Anything the syntax cannot read is rejected with a
 * [KatachiGlobSyntaxException] rather than guessed at.
 *
 * A whole-segment `*` (as in `feature` then `*` below it) is still at least one character in practice, because a
 * real path has no empty level. A `*` mixed with other characters (`app_icon*.xml`) may
 * match nothing and then captures `""`.
 *
 * The same class matches module paths (`:` separated) and file paths (`/` separated): the
 * separator is the only difference, so `*` and `**` cannot come to mean two different things.
 *
 * Matching is case sensitive on every platform, including a case insensitive macOS volume,
 * so that a check gives the same answer locally and on CI.
 *
 * ## Example 1: a `*` never crosses a separator
 * ```kt
 * val glob = Glob.compile("*UseCase.kt")
 * glob.matches("GetUserUseCase.kt") shouldBe true
 * glob.matches("useCase/GetUserUseCase.kt") shouldBe false
 * ```
 */
@InternalKatachiApi
public class Glob private constructor(
    internal val pattern: String,
    internal val separator: Char,
    private val segments: List<String>,
    private val regex: Regex,
    // Lets a pattern count its own wildcards without a second parser (see ModulePattern.wildcardPlaceholders).
    internal val groupKinds: List<GlobGroupKind>,
    internal val groupSegments: List<Int>,
    // Segments before the first wildcard, escapes undone; a rooted pattern (`:feature:*`) reads from its first
    // level.
    internal val literalPrefix: List<String>,
    internal val hasWildcard: Boolean,
) {

    /**
     * Whether [path] matches in full.
     */
    public fun matches(path: String): Boolean = regex.matches(path)

    internal fun match(path: String): GlobMatch? {
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

    // Unlike match, `**` is not split into levels and is kept even when it matched nothing (""), so the n-th entry
    // belongs to the n-th wildcard.
    internal fun matchGroups(path: String): List<String>? {
        val result = regex.matchEntire(path) ?: return null
        return groupKinds.indices.map { index -> result.groupValues.getOrElse(index + 1) { "" } }
    }

    // A `**` that matched no level is null.
    internal fun matchRanges(path: String): List<IntRange?>? {
        val result = regex.matchEntire(path) ?: return null
        return groupKinds.indices.map { index -> result.groups[index + 1]?.range }
    }

    // Module paths only: a `**` in the middle would shift the index of later `*`s from one match to the next.
    internal fun requireAtMostOneTrailingDoubleStar() {
        val positions = segments.indices.filter { segments[it] == DOUBLE_STAR }
        if (positions.size > 1) {
            throw KatachiGlobSyntaxException(pattern, GlobProblem.DoubleStarUsedTooOften(positions.size))
        }
        val position = positions.firstOrNull() ?: return
        if (position != segments.lastIndex) {
            throw KatachiGlobSyntaxException(pattern, GlobProblem.DoubleStarBeforeLastSegment)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is Glob && other.pattern == pattern && other.separator == separator

    override fun hashCode(): Int = 31 * pattern.hashCode() + separator.hashCode()

    override fun toString(): String = "Glob($pattern)"

    internal companion object {
        const val PATH_SEPARATOR: Char = '/'

        const val MODULE_SEPARATOR: Char = ':'

        // A layout of R roles over M modules compiles the same few segment texts R x M times
        // (`src`, `main`, `*Screen.kt`), and the lexer allocates per character: memoising by text
        // is what keeps the allocation per validate() flat. Bounded so a run over many distinct
        // patterns cannot grow the JVM, and keyed by separator too because the same text
        // compiles to a different regex for a module path.
        private const val MEMO_LIMIT: Int = 4096

        private val memo: MutableMap<String, Glob> = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, Glob>(256, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Glob>): Boolean = size > MEMO_LIMIT
            },
        )

        fun compile(pattern: String, separator: Char = PATH_SEPARATOR): Glob {
            val key = "$separator$pattern"
            // Not `getOrPut`: a pattern the lexer refuses must throw every time, never be cached.
            memo[key]?.let { return it }
            return compileUncached(pattern, separator).also { memo[key] = it }
        }

        private fun compileUncached(pattern: String, separator: Char): Glob {
            if (pattern.isEmpty()) throw KatachiGlobSyntaxException(pattern, GlobProblem.EmptyPattern)

            val tokens = GlobLexer.scan(pattern, separator)
            // A leading separator is the pattern being rooted (`:feature:*`), not an empty
            // first segment.
            val leadingSeparator = tokens.first() is GlobToken.Separator
            val segmentTokens = splitIntoSegments(if (leadingSeparator) tokens.drop(1) else tokens)
            if (segmentTokens.any { it.isEmpty() }) {
                throw KatachiGlobSyntaxException(pattern, GlobProblem.EmptySegment(separator))
            }
            val segments = segmentTokens.map { level -> pattern.substring(level.first().start, level.last().end) }

            val escapedSeparator = escapeForRegex(separator)
            val oneLevel = "[^$escapedSeparator]+"
            val manyLevels = "($oneLevel(?:$escapedSeparator$oneLevel)*)"
            val kinds = mutableListOf<GlobGroupKind>()
            val groupSegments = mutableListOf<Int>()
            val expression = StringBuilder()
            if (leadingSeparator) expression.append(escapedSeparator)

            segmentTokens.forEachIndexed { index, level ->
                val groupsBefore = kinds.size
                // A preceding `**` has already emitted the separator that follows it, inside
                // its own optional group, so that `a/**/b` still matches `a/b`.
                val needsSeparator = index > 0 && segmentTokens[index - 1].singleOrNull() !is GlobToken.DoubleStar
                if (level.singleOrNull() is GlobToken.DoubleStar) {
                    kinds += GlobGroupKind.Recursive
                    if (index == segmentTokens.lastIndex) {
                        expression.append(
                            if (needsSeparator) "(?:$escapedSeparator$manyLevels)?" else "$manyLevels?",
                        )
                    } else {
                        if (needsSeparator) expression.append(escapedSeparator)
                        expression.append("(?:$manyLevels$escapedSeparator)?")
                    }
                } else {
                    if (needsSeparator) expression.append(escapedSeparator)
                    expression.append(tokensExpression(level, escapedSeparator, kinds))
                }
                repeat(kinds.size - groupsBefore) { groupSegments += index }
            }

            return Glob(
                pattern = pattern,
                separator = separator,
                segments = segments,
                regex = Regex(expression.toString()),
                groupKinds = kinds.toList(),
                groupSegments = groupSegments.toList(),
                literalPrefix = if (leadingSeparator) emptyList() else literalPrefixOf(segmentTokens),
                hasWildcard = tokens.any { it !is GlobToken.Literal && it !is GlobToken.Separator },
            )
        }

        private fun splitIntoSegments(tokens: List<GlobToken>): List<List<GlobToken>> {
            val segments = mutableListOf<List<GlobToken>>()
            var current = mutableListOf<GlobToken>()
            for (token in tokens) {
                if (token is GlobToken.Separator) {
                    segments += current
                    current = mutableListOf()
                } else {
                    current += token
                }
            }
            segments += current
            return segments
        }

        private const val DOUBLE_STAR: String = "**"

        private const val REGEX_METACHARACTERS: String = "\\.[]{}()*+-?^$|&/<>"

        private fun literalPrefixOf(segments: List<List<GlobToken>>): List<String> {
            val prefix = mutableListOf<String>()
            for (segment in segments) {
                if (!segment.all { it is GlobToken.Literal }) break
                prefix += segment.joinToString("") { (it as GlobToken.Literal).character.toString() }
            }
            return prefix
        }

        private fun escapeForRegex(character: Char): String =
            if (character in REGEX_METACHARACTERS) "\\$character" else character.toString()

        // kinds gets one entry per capturing `*`, so the n-th regex group is the n-th entry.
        private fun tokensExpression(
            tokens: List<GlobToken>,
            escapedSeparator: String,
            kinds: MutableList<GlobGroupKind>,
        ): String {
            val expression = StringBuilder()
            for (token in tokens) {
                when (token) {
                    is GlobToken.Literal -> expression.append(escapeForRegex(token.character))
                    is GlobToken.Star -> if (token.capturing) {
                        // Zero characters or more, never crossing a separator.
                        expression.append("([^$escapedSeparator]*)")
                        kinds += GlobGroupKind.Single
                    } else {
                        expression.append("[^$escapedSeparator]*")
                    }
                    is GlobToken.Question -> expression.append("[^$escapedSeparator]")
                    is GlobToken.CharClass -> expression.append(classExpression(token, escapedSeparator))
                    is GlobToken.Alternation -> expression.append(
                        token.alternatives.joinToString("|", prefix = "(?:", postfix = ")") {
                            tokensExpression(it, escapedSeparator, kinds)
                        },
                    )
                    // The scanner keeps these out of a segment's tokens (and a `**` is handled by the caller).
                    is GlobToken.DoubleStar, is GlobToken.Separator -> Unit
                }
            }
            return expression.toString()
        }

        private fun classExpression(token: GlobToken.CharClass, escapedSeparator: String): String {
            val members = token.members.joinToString("") { member ->
                when (member) {
                    is GlobClassMember.Single -> escapeForRegex(member.character)
                    is GlobClassMember.Range -> "${escapeForRegex(member.from)}-${escapeForRegex(member.to)}"
                }
            }
            return when {
                token.negated -> "[^$members$escapedSeparator]"
                // A range can run across the separator's code point, which must still not match.
                token.members.any { it is GlobClassMember.Range } -> "[$members&&[^$escapedSeparator]]"
                else -> "[$members]"
            }
        }
    }
}
