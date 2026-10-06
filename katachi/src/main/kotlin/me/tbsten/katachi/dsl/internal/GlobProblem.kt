package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role

/** Characters that mean themselves once written as `\<char>`. */
internal const val GLOB_ESCAPABLE: String = "*\\{}?[],"

/**
 * Why a pattern could not be read.
 *
 * One subtype per thing that can go wrong, each holding what its own sentence needs, so that
 * the wording of every glob failure lives in this one file rather than at the twelve places
 * that raise one.
 *
 * This is katachi's own classification of its glob syntax and is expected to grow and change
 * with it, which is why it is not part of the supported surface.
 *
 * ## Example 1: tell which kind of problem a broken pattern raised
 * ```kt
 * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("") }
 *     .problem shouldBe GlobProblem.EmptyPattern
 * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("a**b") }
 *     .problem.shouldBeInstanceOf<GlobProblem.DoubleStarInsideSegment>()
 * ```
 */
internal sealed interface GlobProblem {
    /**
     * The sentence this problem contributes, for [pattern] as it was written.
     *
     * ## Example 1: read a problem's sentence directly
     * ```kt
     * GlobProblem.EmptyPattern.explain("") shouldBe "A glob pattern must not be empty."
     * ```
     */
    fun explain(pattern: String): String

    /**
     * Nothing was written at all.
     *
     * ## Example 1: an empty pattern
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("") }
     *     .problem shouldBe GlobProblem.EmptyPattern
     * ```
     */
    object EmptyPattern : GlobProblem {
        override fun explain(pattern: String): String = "A glob pattern must not be empty."
    }

    /**
     * Two separators in a row, or a trailing one.
     *
     * ## Example 1: two separators in a row
     * ```kt
     * val problem = shouldThrow<KatachiGlobSyntaxException> { Glob.compile("a//b") }.problem
     * problem.shouldBeInstanceOf<GlobProblem.EmptySegment>().separator shouldBe '/'
     * ```
     */
    class EmptySegment(
        val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has an empty segment. Two `$separator` in a row, or a trailing " +
                "`$separator`, matches nothing."
    }

    /**
     * A segment ends with a lone `\`, which escapes nothing.
     *
     * ## Example 1: a pattern ending in a lone backslash
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("""a\""") }
     *     .problem shouldBe GlobProblem.TrailingBackslash
     * ```
     */
    object TrailingBackslash : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` ends a segment with `\\`. Write `\\\\` for a literal backslash."
    }

    /**
     * `\` in front of a character katachi does not treat as a metacharacter.
     *
     * ## Example 1: escaping a character that is not a metacharacter
     * ```kt
     * val problem = shouldThrow<KatachiGlobSyntaxException> { Glob.compile("""\a.kt""") }.problem
     * problem.shouldBeInstanceOf<GlobProblem.UnescapableCharacter>().character shouldBe 'a'
     * ```
     */
    class UnescapableCharacter(
        val character: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` escapes `$character`, which katachi does not treat as a " +
                "metacharacter. Only `$GLOB_ESCAPABLE` can be escaped."
    }

    /**
     * `**` written as part of a larger segment, where it cannot mean "zero levels or more".
     *
     * ## Example 1: `**` glued to the rest of a segment
     * ```kt
     * val problem = shouldThrow<KatachiGlobSyntaxException> { Glob.compile("a**b") }.problem
     * problem.shouldBeInstanceOf<GlobProblem.DoubleStarInsideSegment>().segment shouldBe "a**b"
     * ```
     */
    class DoubleStarInsideSegment(
        val segment: String,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` as part of the segment `$segment`. `**` means " +
                "\"zero levels or more\" and only makes sense as a whole segment; " +
                "use a single `*` to match part of a name."
    }

    /**
     * A `[` that never reaches its `]`.
     *
     * ## Example 1: a `[` with no `]`
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("a[bc") }.problem.shouldBeInstanceOf<GlobProblem.UnclosedBracket>()
     * ```
     */
    class UnclosedBracket(
        val segment: String,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` opens `[` in `$segment` and never closes it. `[...]` matches one " +
                "character and needs its `]`; close it, or write `\\[` for a literal `[`."
    }

    /**
     * A `{` that never reaches its `}`.
     *
     * ## Example 1: a `{` with no `}`
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("a{b,c") }.problem.shouldBeInstanceOf<GlobProblem.UnclosedBrace>()
     * ```
     */
    class UnclosedBrace(
        val segment: String,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` opens `{` in `$segment` and never closes it. `{a,b}` needs its `}`; " +
                "close it, or write `\\{` for a literal `{`."
    }

    /**
     * A `}` or `]` that closes nothing.
     *
     * ## Example 1: a lone `]`
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("a]") }.problem.shouldBeInstanceOf<GlobProblem.UnmatchedClosing>().character shouldBe ']'
     * ```
     */
    class UnmatchedClosing(
        val character: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has a `$character` that closes nothing. Write `\\$character` for a " +
                "literal `$character`."
    }

    /**
     * A `[...]` with no character in it.
     *
     * ## Example 1: an empty class
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("[]") }.problem shouldBe GlobProblem.EmptyCharacterClass
     * ```
     */
    object EmptyCharacterClass : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has a `[...]` with no character in it, which can match nothing. List " +
                "at least one character, or write `\\[\\]` for the literal text."
    }

    /**
     * A range whose first character comes after its last.
     *
     * ## Example 1: a reversed range
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("[z-a]") }.problem.shouldBeInstanceOf<GlobProblem.ReversedRange>().from shouldBe 'z'
     * ```
     */
    class ReversedRange(
        val from: Char,
        val to: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has the range `$from-$to`, whose first character comes after its " +
                "last. Write it as `$to-$from`."
    }

    /**
     * A level separator written inside `[...]`.
     *
     * ## Example 1: a separator inside a class
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("[a/b]") }.problem.shouldBeInstanceOf<GlobProblem.SeparatorInBrackets>()
     * ```
     */
    class SeparatorInBrackets(
        val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` puts `$separator` inside `[...]`. `[...]` matches one character " +
                "inside a single level and never the separator; write the levels apart instead."
    }

    /**
     * A `{}` that lists no alternative.
     *
     * ## Example 1: empty braces
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("{}") }.problem shouldBe GlobProblem.EmptyBraces
     * ```
     */
    object EmptyBraces : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has `{}`, which lists no alternative. Write at least one, such as " +
                "`{a,b}`, or `{,b}` for \"nothing or b\"."
    }

    /**
     * A `{` written inside another `{...}`.
     *
     * ## Example 1: nested braces
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("{a,{b}}") }.problem shouldBe GlobProblem.NestedBraces
     * ```
     */
    object NestedBraces : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` opens `{` inside another `{...}`. Alternatives do not nest; write " +
                "them out in one list, such as `{a,b,c}`."
    }

    /**
     * A `**` written inside `{...}`.
     *
     * ## Example 1: `**` in braces
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("{**}") }.problem shouldBe GlobProblem.DoubleStarInBraces
     * ```
     */
    object DoubleStarInBraces : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` inside `{...}`. An alternative stays within one level; " +
                "write `**` as a level of its own."
    }

    /**
     * A level separator written inside `{...}`.
     *
     * ## Example 1: a separator inside braces
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { Glob.compile("{a/b,c}") }.problem.shouldBeInstanceOf<GlobProblem.SeparatorInBraces>()
     * ```
     */
    class SeparatorInBraces(
        val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` puts `$separator` inside `{...}`. An alternative stays within one " +
                "level; write one layout key per alternative instead."
    }

    /**
     * A separator that is itself one of the glob's own characters, which the pattern could then
     * neither split on nor escape reliably.
     *
     * Only `NamePattern.compile` lets a caller pick the separator; the layout DSL always uses
     * `/` or `:`.
     *
     * ## Example 1: a name pattern split on `*`
     * ```kt
     * val problem = shouldThrow<KatachiGlobSyntaxException> { NamePattern.compile("a*b", separator = '*') }.problem
     * problem.shouldBeInstanceOf<GlobProblem.UnusableSeparator>().separator shouldBe '*'
     * ```
     */
    class UnusableSeparator(
        val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` is to be split on `$separator`, but katachi's glob already gives " +
                "`$separator` a meaning. Use a separator other than `$GLOB_ESCAPABLE`, or none."
    }

    /**
     * A module path pattern uses `**` more than once.
     *
     * ## Example 1: a module pattern with `**` twice
     * ```kt
     * val problem = shouldThrow<KatachiGlobSyntaxException> {
     *     ModulePattern.compile(":**:feature:**")
     * }.problem
     * problem.shouldBeInstanceOf<GlobProblem.DoubleStarUsedTooOften>().count shouldBe 2
     * ```
     */
    class DoubleStarUsedTooOften(
        val count: Int,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` $count times. A module path may use `**` at " +
                "most once, as its last segment, so that the index of each captured " +
                "wildcard is the same for every match."
    }

    /**
     * A module path pattern uses `**` somewhere other than as its last segment.
     *
     * ## Example 1: `**` written before the last segment
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { ModulePattern.compile(":feature:**:impl") }
     *     .problem shouldBe GlobProblem.DoubleStarBeforeLastSegment
     * ```
     */
    object DoubleStarBeforeLastSegment : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` before its last segment. A module path may only use `**` " +
                "as its last segment, so that the index of each captured wildcard is the " +
                "same for every match."
    }

    /**
     * A module path was written as an empty string.
     *
     * ## Example 1: an empty module path
     * ```kt
     * shouldThrow<KatachiGlobSyntaxException> { ModulePath.of("") }
     *     .problem shouldBe GlobProblem.EmptyModulePath
     * ```
     */
    object EmptyModulePath : GlobProblem {
        override fun explain(pattern: String): String =
            "A module path must not be empty. Write `\":\"` for the root project."
    }

    /**
     * Two `:` in a row, or a trailing one, in a module path.
     *
     * ## Example 1: two `:` in a row
     * ```kt
     * val problem = shouldThrow<KatachiGlobSyntaxException> { ModulePath.of(":core::data") }.problem
     * problem.shouldBeInstanceOf<GlobProblem.EmptyModuleName>().separator shouldBe ':'
     * ```
     */
    class EmptyModuleName(
        val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has an empty module name. Two `$separator` in a row, or a trailing " +
                "`$separator`, names no module."
    }

    /**
     * A wildcard where exactly one module had to be named.
     *
     * ## Example 1: a wildcard in a module path
     * ```kt
     * val problem = shouldThrow<KatachiGlobSyntaxException> { ModulePath.of(":feature:*") }.problem
     * problem.shouldBeInstanceOf<GlobProblem.WildcardInModuleName>().moduleName shouldBe "*"
     * ```
     */
    class WildcardInModuleName(
        val moduleName: String,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `*` in the module name `$moduleName`. A module path names one " +
                "module; a pattern that matches several is expanded before this point."
    }

    /**
     * A layout key with two `/` in a row, a leading `/`, or a trailing one.
     *
     * ## Example 1: two `/` in a row inside a layout key
     * ```kt
     * val arch = architecture {
     *     "group".group {
     *         "Role".role { layout { "app//ios" { } } }
     *     }
     * }
     * shouldThrow<KatachiGlobSyntaxException> { arch.flattenLayout() }
     *     .problem shouldBe GlobProblem.EmptyLayoutKeyLevel
     * ```
     */
    object EmptyLayoutKeyLevel : GlobProblem {
        override fun explain(pattern: String): String =
            "The layout key `$pattern` has an empty level. Two `/` in a row, a leading `/`, or a " +
                "trailing `/` matches nothing; paths in `layout { }` are always relative to the " +
                "project root."
    }
}

/**
 * Where the pattern that could not be read was written.
 *
 * `layout { }` blocks are deferred, so a bad key is noticed long after the line that wrote it
 * ran and the stack no longer points anywhere useful. A context puts that line back in front
 * of the problem's own sentence.
 *
 * Like [GlobProblem], this is katachi's own bookkeeping rather than a supported surface.
 *
 * ## Example 1: a broken layout key reported with where it was declared
 * ```kt
 * val arch = architecture {
 *     "group".group {
 *         "Role".role { layout { "{a".file() } }
 *     }
 * }
 * shouldThrow<KatachiGlobSyntaxException> { arch.flattenLayout() }
 *     .message.shouldNotBeNull() shouldContain "Role group.Role declares the layout path"
 * ```
 */
internal sealed interface GlobContext {
    /**
     * The sentence placed before the problem's own.
     *
     * ## Example 1: read a context's sentence directly
     * ```kt
     * val arch = architecture {
     *     "group".group {
     *         "Role".role { layout { "{a".file() } }
     *     }
     * }
     * val context = shouldThrow<KatachiGlobSyntaxException> { arch.flattenLayout() }
     *     .context.shouldBeInstanceOf<GlobContext.RoleLayoutPath>()
     * context.describe() shouldContain "Role group.Role declares the layout path"
     * ```
     */
    fun describe(): String

    /**
     * A path pattern written in the `layout { }` of one role.
     *
     * ## Example 1: read which role and path a failure came from
     * ```kt
     * val arch = architecture {
     *     "group".group {
     *         "Role".role { layout { "{a".file() } }
     *     }
     * }
     * val context = shouldThrow<KatachiGlobSyntaxException> { arch.flattenLayout() }
     *     .context.shouldBeInstanceOf<GlobContext.RoleLayoutPath>()
     * context.role.qualifiedName shouldBe "group.Role"
     * context.path shouldBe "{a"
     * ```
     */
    class RoleLayoutPath(
        val role: Role,
        val path: String,
        val declaredAt: DeclarationSite,
    ) : GlobContext {
        override fun describe(): String =
            "Role ${role.qualifiedName} declares the layout path `$path` at $declaredAt."
    }

    /**
     * A module path written as a `"...".module { }` key.
     *
     * ## Example 1: read which `.module { }` key a failure came from
     * ```kt
     * val arch = architecture {
     *     "group".group {
     *         "Role".role { layout { ":core::data".module { } } }
     *     }
     * }
     * val context = shouldThrow<KatachiGlobSyntaxException> { arch.flattenLayout() }
     *     .context.shouldBeInstanceOf<GlobContext.LayoutModulePath>()
     * context.key shouldBe ":core::data"
     * ```
     */
    class LayoutModulePath(
        val key: String,
        val declaredAt: DeclarationSite,
    ) : GlobContext {
        override fun describe(): String = "The layout declares the module `$key` at $declaredAt."
    }
}
