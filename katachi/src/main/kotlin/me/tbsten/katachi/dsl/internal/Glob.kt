package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException

/**
 * What a pattern captured when it matched.
 *
 * ## Example 1: read what a trailing `**` captured, one element per level
 * ```kt
 * Glob.compile(":feature:**", Glob.MODULE_SEPARATOR).match(":feature:home:impl")
 *     .shouldNotBeNull().wildcards shouldContainExactly listOf("home", "impl")
 * ```
 */
internal class GlobMatch(
    /**
     * One element per `*`, and one element **per level** for a `**`, in the order the
     * wildcards appear in the pattern. A `**` that matched no level contributes nothing, so
     * `":feature:**"` against `:feature` captures an empty list.
     */
    val wildcards: List<String>,
) {
    override fun toString(): String = "GlobMatch($wildcards)"
}

internal enum class GlobGroupKind { Single, Recursive }

/**
 * katachi's glob, translated to a regular expression.
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
 * [GlobProblem] rather than guessed at.
 *
 * A whole-segment `*` (as in `feature` then `*` below it) is still at least one character in practice, because a
 * real path has no empty level. A `*` mixed with other characters (`app_icon*.xml`) may
 * match nothing and then captures `""`. Syntax is read by [GlobLexer].
 *
 * The same class matches module paths (`:` separated) and file paths (`/` separated): the
 * separator is the only difference, so `*` and `**` cannot come to mean two different things.
 *
 * Matching is case sensitive on every platform, including a case insensitive macOS volume,
 * so that a check gives the same answer locally and on CI.
 *
 * The JDK's `PathMatcher` is not used: it answers yes or no, and katachi has to hand the
 * matched parts back to the user as `wildcards`.
 *
 * ## Example 1: the same syntax reads a module path and a file path
 * ```kt
 * val glob = Glob.compile(":feature:*", Glob.MODULE_SEPARATOR)
 * glob.matches(":feature:home") shouldBe true
 * glob.matches(":feature") shouldBe false
 * ```
 */
@InternalKatachiApi
public class Glob private constructor(
    /** The pattern as written. Two patterns are the same only when these strings are equal. */
    internal val pattern: String,
    /** `/` for file paths, `:` for module paths. */
    internal val separator: Char,
    private val segments: List<String>,
    private val regex: Regex,
    /**
     * One entry per capturing wildcard, in the order the wildcards were written.
     *
     * Read back so that a pattern can count its own wildcards without a second parser — see
     * [ModulePattern.wildcardPlaceholders], which needs to know how many a match *would* have
     * captured before any match has happened.
     */
    internal val groupKinds: List<GlobGroupKind>,
    /**
     * For each entry of [groupKinds], the segment it sits in: 0-based, counted after a leading
     * separator, so `":feature:*:**"` reads `[1, 2]`.
     *
     * What lets a caller tell which level a wildcard captured, for a name given to that level
     * (see `LayoutEntry.captureValuesOf`) rather than to the n-th wildcard of the whole path.
     */
    internal val groupSegments: List<Int>,
    /**
     * The segments before the first one holding a wildcard, with their escapes undone: what
     * any path this pattern matches has to start with, level for level.
     *
     * Empty for a rooted pattern (`:feature:*`), which is read from its first level instead
     * of from the separator in front of it. Lets an index hand a path only the patterns
     * that could match it without a second parser.
     */
    internal val literalPrefix: List<String>,
    /**
     * Whether the pattern contains an unescaped `*`, `**`, `?`, `[` or `{`. Such a declaration
     * is optional by nature.
     *
     * ## Example 1: tell a literal pattern apart from one with a wildcard
     * ```kt
     * Glob.compile("build.gradle.kts").hasWildcard shouldBe false
     * Glob.compile("*.kt").hasWildcard shouldBe true
     * Glob.compile("build.gradle{,.kts}").hasWildcard shouldBe true
     * ```
     */
    internal val hasWildcard: Boolean,
) {

    /**
     * Whether [path] matches in full.
     *
     * ## Example 1: a wildcard never crosses into a path it does not fully cover
     * ```kt
     * val glob = Glob.compile("*UseCase.kt")
     * glob.matches("GetUserUseCase.kt") shouldBe true
     * glob.matches("useCase/GetUserUseCase.kt") shouldBe false
     * ```
     */
    public fun matches(path: String): Boolean = regex.matches(path)

    /**
     * The match and what it captured, or `null` when [path] does not match.
     *
     * ## Example 1: read back what a single `*` captured
     * ```kt
     * Glob.compile("*UseCase.kt").match("GetUserUseCase.kt")
     *     .shouldNotBeNull().wildcards shouldContainExactly listOf("GetUser")
     * ```
     */
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

    /**
     * The raw text each wildcard matched, one entry per [groupKinds] entry and in the same order,
     * or `null` when [path] does not match.
     *
     * Unlike [match], a `**` is not split into levels and is kept even when it matched nothing
     * (as `""`), so that the n-th entry always belongs to the n-th wildcard -- the shape
     * [groupSegments] is indexed by.
     *
     * ## Example 1: read what each wildcard of a module path matched
     * ```kt
     * Glob.compile(":feature:*:**", Glob.MODULE_SEPARATOR).matchGroups(":feature:home:ui:list") shouldBe
     *     listOf("home", "ui:list")
     * Glob.compile(":feature:*:**", Glob.MODULE_SEPARATOR).matchGroups(":feature:home") shouldBe
     *     listOf("home", "")
     * ```
     */
    internal fun matchGroups(path: String): List<String>? {
        val result = regex.matchEntire(path) ?: return null
        return groupKinds.indices.map { index -> result.groupValues.getOrElse(index + 1) { "" } }
    }

    /**
     * Where each capturing wildcard matched in [path], one entry per [groupKinds] entry in the
     * same order, or `null` when [path] does not match. A `**` that matched no level is `null`.
     *
     * Lets a caller swap only the captured parts of the path for something else and keep the
     * rest (the `?`, `[...]` and `{...}` characters, and the unnamed wildcards) as the file has them.
     *
     * ## Example 1: find where the `*` of a name matched
     * ```kt
     * Glob.compile("app_icon*.xml").matchRanges("app_icon_dark.xml") shouldBe listOf(8..12)
     * ```
     */
    internal fun matchRanges(path: String): List<IntRange?>? {
        val result = regex.matchEntire(path) ?: return null
        return groupKinds.indices.map { index -> result.groups[index + 1]?.range }
    }

    /**
     * Rejects a pattern whose `**` is anywhere but at the end, or that has more than one.
     *
     * Module paths call this. Flattening `**` level by level means every level it matches
     * lands at the end of `wildcards`, so a `**` in the middle would shift the index of the
     * `*` after it from one match to the next.
     *
     * ## Example 1: reject a `**` that is not the pattern's last segment
     * ```kt
     * val glob = Glob.compile(":feature:**:impl", Glob.MODULE_SEPARATOR)
     * shouldThrow<KatachiGlobSyntaxException> { glob.requireAtMostOneTrailingDoubleStar() }
     * ```
     */
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

    /**
     * Where a [Glob] is built: [compile] parses a pattern, and [PATH_SEPARATOR] /
     * [MODULE_SEPARATOR] are the separators the two DSLs give it.
     *
     * ## Example 1: compile the same syntax for a file path and a module path
     * ```kt
     * Glob.compile("*.kt", Glob.PATH_SEPARATOR).matches("Foo.kt") shouldBe true
     * Glob.compile(":feature:*", Glob.MODULE_SEPARATOR).matches(":feature:home") shouldBe true
     * ```
     */
    internal companion object {
        /**
         * Separator of a file path.
         *
         * ## Example 1: compile a file path pattern with it
         * ```kt
         * val glob = Glob.compile("core/data/Repository.kt", Glob.PATH_SEPARATOR)
         * glob.matches("core/data/Repository.kt") shouldBe true
         * ```
         */
        const val PATH_SEPARATOR: Char = '/'

        /**
         * Separator of a Gradle module path.
         *
         * ## Example 1: compile a module path pattern with it
         * ```kt
         * val glob = Glob.compile(":feature:*", Glob.MODULE_SEPARATOR)
         * glob.matches(":feature:home") shouldBe true
         * ```
         */
        const val MODULE_SEPARATOR: Char = ':'

        /**
         * Translates [pattern] into a regular expression.
         *
         * ## Example 1: compile a pattern whose `**` matches any depth of path
         * ```kt
         * val glob = Glob.compile("**")
         * glob.matches("useCase") shouldBe true
         * glob.matches("useCase/GetUserUseCase.kt") shouldBe true
         * ```
         *
         * @throws KatachiGlobSyntaxException when the pattern is empty, has an empty segment,
         *   uses `**` as part of a larger segment, or has a `[...]` or `{...}` that cannot be read.
         */
        fun compile(pattern: String, separator: Char = PATH_SEPARATOR): Glob {
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

        /** The segments before the first one holding a wildcard, with their escapes undone. */
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

        /**
         * The regular expression of one segment. [kinds] gets one entry per capturing `*`, so
         * that the n-th group of the whole expression is the n-th entry.
         */
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
