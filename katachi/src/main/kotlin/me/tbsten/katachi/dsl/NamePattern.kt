package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.GLOB_ESCAPABLE
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.internal.GlobProblem

/**
 * A name or a dotted path, matched the way `layout { }` matches a path: `*` is zero characters or
 * more within one level, `**` is any number of levels, and `?`, `[abc]` and `{a,b}` are one
 * character, one character of a list and one of a few alternatives.
 *
 * It is the glob of a layout key, opened up for a backend that matches something other than a
 * file path -- a declaration name, an import, a package. A second matcher would drift apart from
 * the first, so this one wraps the same glob: every wildcard reads as it does in a layout key, a
 * malformed `{` or `[` is refused the same way, and `\` makes the next character literal.
 * In a type, a `?` stays the nullable marker rather than a one-character wildcard.
 *
 * The separator says what a level is. With `'.'`, `kotlinx.coroutines.*` matches
 * `kotlinx.coroutines.launch` but not `kotlinx.coroutines.flow.Flow`, and `**` crosses the dots.
 * With no separator (`null`, the default) there are no levels, so `*` matches any text and `**`
 * means the same as `*`. Matching is case sensitive.
 *
 * ## Example 1: match import paths level by level
 * ```kt
 * val coroutines = NamePattern.compile("kotlinx.coroutines.*", separator = '.')
 * coroutines.matches("kotlinx.coroutines.launch") shouldBe true
 * coroutines.matches("kotlinx.coroutines.flow.Flow") shouldBe false
 * NamePattern.compile("kotlinx.**", separator = '.').matches("kotlinx.coroutines.flow.Flow") shouldBe true
 * ```
 *
 * ## Example 2: match a declaration name, where `*` matches any text
 * ```kt
 * val impl = NamePattern.compile("*Impl")
 * impl.matches("UserRepositoryImpl") shouldBe true
 * impl.hasWildcard shouldBe true
 * NamePattern.compile("HomeViewModel").hasWildcard shouldBe false
 * ```
 *
 * @see LayoutScope for the same glob written as a layout key.
 */
@ExperimentalKatachiApi
public class NamePattern private constructor(
    private val glob: Glob,
) {
    /**
     * The pattern as it was written, escapes included.
     *
     * ## Example 1: read back what was compiled
     * ```kt
     * NamePattern.compile("""Foo\*""").pattern shouldBe """Foo\*"""
     * ```
     */
    public val pattern: String get() = glob.pattern

    /**
     * What a level is split on, or `null` when the pattern has no levels.
     *
     * ## Example 1: tell an import pattern from a name pattern
     * ```kt
     * NamePattern.compile("kotlin.**", separator = '.').separator shouldBe '.'
     * NamePattern.compile("*Impl").separator shouldBe null
     * ```
     */
    public val separator: Char? get() = glob.separator.takeUnless { it == NO_SEPARATOR }

    /**
     * Whether the pattern holds a `*` or a `**`. An escaped `\*` does not count: a pattern
     * without a wildcard matches exactly one text, which is what makes a line naming it required.
     *
     * ## Example 1: tell a name that is fixed from one that is not
     * ```kt
     * NamePattern.compile("HomeViewModel").hasWildcard shouldBe false
     * NamePattern.compile("*ViewModel").hasWildcard shouldBe true
     * NamePattern.compile("""Star\*""").hasWildcard shouldBe false
     * ```
     */
    public val hasWildcard: Boolean get() = glob.hasWildcard

    /**
     * Whether [text] matches the whole pattern.
     *
     * ## Example 1: a `*` never crosses a separator
     * ```kt
     * val pattern = NamePattern.compile("com.example.*", separator = '.')
     * pattern.matches("com.example.App") shouldBe true
     * pattern.matches("com.example.feature.Home") shouldBe false
     * ```
     */
    public fun matches(text: String): Boolean = glob.matches(text)

    /** Equal when the pattern and the separator are. */
    override fun equals(other: Any?): Boolean = other is NamePattern && other.glob == glob

    /** Consistent with [equals]: the hash of the pattern and the separator. */
    override fun hashCode(): Int = glob.hashCode()

    /** `NamePattern(<pattern>)`. */
    override fun toString(): String = "NamePattern($pattern)"

    /**
     * Where a [NamePattern] comes from: [compile].
     *
     * ## Example 1: compile a name pattern and an import pattern
     * ```kt
     * val name = NamePattern.compile("*UseCase")
     * val import = NamePattern.compile("kotlinx.coroutines.**", separator = '.')
     * ```
     */
    public companion object {
        /**
         * Reads [pattern], refusing it when it is not one katachi's glob can read.
         *
         * ## Example 1: compile, then match
         * ```kt
         * NamePattern.compile("on*").matches("onRefresh") shouldBe true
         * NamePattern.compile("on{Click,Tap}").matches("onTap") shouldBe true
         * shouldThrow<KatachiGlobSyntaxException> { NamePattern.compile("on{Click") }
         * ```
         *
         * @param pattern the pattern, in the glob syntax of a layout key.
         * @param separator what one level is split on -- `'.'` for an import or a package --
         *   or `null` for a name, which has no levels.
         * @throws KatachiGlobSyntaxException when [pattern] is empty, has an empty level
         *   (`a..b`), uses `**` inside a level, leaves a `{` or `[` unclosed or a `}` or `]`
         *   unmatched, writes a malformed `[...]` or `{...}`, ends a level with a lone `\`, or when [separator] is one of the glob's own characters.
         */
        public fun compile(pattern: String, separator: Char? = null): NamePattern {
            if (separator != null && separator in GLOB_ESCAPABLE) {
                throw KatachiGlobSyntaxException(pattern, GlobProblem.UnusableSeparator(separator))
            }
            return NamePattern(Glob.compile(pattern, separator ?: NO_SEPARATOR))
        }
    }
}

/**
 * The separator a pattern without levels is compiled with: a character no name, import or
 * package spells, so splitting on it never cuts anything.
 */
private const val NO_SEPARATOR: Char = '\u0000'
