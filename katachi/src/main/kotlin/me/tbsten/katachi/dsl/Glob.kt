package me.tbsten.katachi.dsl

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException

/**
 * A glob pattern katachi cannot make sense of.
 *
 * The wording is [problem]'s, and [context] adds where the pattern was written when that is
 * known. Both are internal bookkeeping; [pattern] is the part a caller can rely on.
 *
 * @property pattern the pattern as it was written.
 *
 * ## Example 1: catch a broken pattern and read back what was written
 * ```kt
 * shouldThrow<KatachiGlobSyntaxException> { ModulePath.of("") }
 *     .pattern shouldBe ""
 * ```
 *
 * ## Example 2: catch a broken pattern by its message
 * ```kt
 * shouldThrow<KatachiGlobSyntaxException> { ModulePath.of("") }
 *     .message.shouldNotBeNull() shouldContain "must not be empty"
 * ```
 */
public class KatachiGlobSyntaxException internal constructor(
    public val pattern: String,
    @property:InternalKatachiApi public val problem: GlobProblem,
    @property:InternalKatachiApi public val context: GlobContext? = null,
) : KatachiDeclarationException(globSyntaxMessage(pattern, problem, context))

/** The context's sentence, when there is one, in front of the problem's own. */
private fun globSyntaxMessage(pattern: String, problem: GlobProblem, context: GlobContext?): String =
    if (context == null) problem.explain(pattern) else "${context.describe()} ${problem.explain(pattern)}"

/**
 * What a pattern captured when it matched.
 *
 * ## Example 1: read what a trailing `**` captured, one element per level
 * ```kt
 * Glob.compile(":feature:**", Glob.MODULE_SEPARATOR).match(":feature:home:impl")
 *     .shouldNotBeNull().wildcards shouldContainExactly listOf("home", "impl")
 * ```
 */
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
    public val pattern: String,
    /** `/` for file paths, `:` for module paths. */
    public val separator: Char,
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
) {
    /**
     * Whether the pattern contains a `*` or a `**`. Such a declaration is optional by nature.
     *
     * ## Example 1: tell a literal pattern apart from one with a wildcard
     * ```kt
     * Glob.compile("build.gradle.kts").hasWildcard shouldBe false
     * Glob.compile("*.kt").hasWildcard shouldBe true
     * ```
     */
    public val hasWildcard: Boolean get() = groupKinds.isNotEmpty()

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
     *
     * ## Example 1: reject a `**` that is not the pattern's last segment
     * ```kt
     * val glob = Glob.compile(":feature:**:impl", Glob.MODULE_SEPARATOR)
     * shouldThrow<KatachiGlobSyntaxException> { glob.requireAtMostOneTrailingDoubleStar() }
     * ```
     */
    public fun requireAtMostOneTrailingDoubleStar() {
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
    public companion object {
        /**
         * Separator of a file path.
         *
         * ## Example 1: compile a file path pattern with it
         * ```kt
         * val glob = Glob.compile("core/data/Repository.kt", Glob.PATH_SEPARATOR)
         * glob.matches("core/data/Repository.kt") shouldBe true
         * ```
         */
        public const val PATH_SEPARATOR: Char = '/'

        /**
         * Separator of a Gradle module path.
         *
         * ## Example 1: compile a module path pattern with it
         * ```kt
         * val glob = Glob.compile(":feature:*", Glob.MODULE_SEPARATOR)
         * glob.matches(":feature:home") shouldBe true
         * ```
         */
        public const val MODULE_SEPARATOR: Char = ':'

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
         *   uses `**` as part of a larger segment, or uses a metacharacter katachi does not
         *   have.
         */
        public fun compile(pattern: String, separator: Char = PATH_SEPARATOR): Glob {
            if (pattern.isEmpty()) throw KatachiGlobSyntaxException(pattern, GlobProblem.EmptyPattern)

            // A leading separator is the pattern being rooted (`:feature:*`), not an empty
            // first segment.
            val leadingSeparator = pattern[0] == separator
            val body = if (leadingSeparator) pattern.substring(1) else pattern
            val segments = body.split(separator)
            if (segments.any { it.isEmpty() }) {
                throw KatachiGlobSyntaxException(pattern, GlobProblem.EmptySegment(separator))
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
                        val escaped = segment.getOrNull(index + 1)
                            ?: throw KatachiGlobSyntaxException(pattern, GlobProblem.TrailingBackslash)
                        if (escaped !in GLOB_ESCAPABLE) {
                            throw KatachiGlobSyntaxException(
                                pattern,
                                GlobProblem.UnescapableCharacter(escaped),
                            )
                        }
                        expression.append(escapeForRegex(escaped))
                        index += 2
                    }

                    character == '*' -> {
                        if (segment.getOrNull(index + 1) == '*') {
                            throw KatachiGlobSyntaxException(
                                pattern,
                                GlobProblem.DoubleStarInsideSegment(segment),
                            )
                        }
                        // One or more characters, never crossing a separator. A `*` never
                        // captures the empty string, so `wildcards` never holds a blank.
                        expression.append("([^$escapedSeparator]+)")
                        kinds += GlobGroupKind.Single
                        index++
                    }

                    character in REJECTED -> throw KatachiGlobSyntaxException(
                        pattern,
                        GlobProblem.RejectedMetacharacter(character),
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
