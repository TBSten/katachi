package me.tbsten.katachi.dsl

import me.tbsten.katachi.InternalKatachiApi

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
 */
@InternalKatachiApi
public sealed interface GlobProblem {
    /** The sentence this problem contributes, for [pattern] as it was written. */
    public fun explain(pattern: String): String

    /** Nothing was written at all. */
    @InternalKatachiApi
    public object EmptyPattern : GlobProblem {
        override fun explain(pattern: String): String = "A glob pattern must not be empty."
    }

    /** Two separators in a row, or a trailing one. */
    @InternalKatachiApi
    public class EmptySegment internal constructor(
        public val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has an empty segment. Two `$separator` in a row, or a trailing " +
                "`$separator`, matches nothing."
    }

    /** A segment ends with a lone `\`, which escapes nothing. */
    @InternalKatachiApi
    public object TrailingBackslash : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` ends a segment with `\\`. Write `\\\\` for a literal backslash."
    }

    /** `\` in front of a character katachi does not treat as a metacharacter. */
    @InternalKatachiApi
    public class UnescapableCharacter internal constructor(
        public val character: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` escapes `$character`, which katachi does not treat as a " +
                "metacharacter. Only `$GLOB_ESCAPABLE` can be escaped."
    }

    /** `**` written as part of a larger segment, where it cannot mean "zero levels or more". */
    @InternalKatachiApi
    public class DoubleStarInsideSegment internal constructor(
        public val segment: String,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` as part of the segment `$segment`. `**` means " +
                "\"zero levels or more\" and only makes sense as a whole segment; " +
                "use a single `*` to match part of a name."
    }

    /** A metacharacter of another tool's glob, refused rather than silently read as a literal. */
    @InternalKatachiApi
    public class RejectedMetacharacter internal constructor(
        public val character: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `$character`. katachi's glob has only `*` and `**`; write " +
                "`\\$character` for a literal `$character`, or write one layout key per " +
                "alternative."
    }

    /** A module path pattern uses `**` more than once. */
    @InternalKatachiApi
    public class DoubleStarUsedTooOften internal constructor(
        public val count: Int,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` $count times. A module path may use `**` at " +
                "most once, as its last segment, so that the index of each captured " +
                "wildcard is the same for every match."
    }

    /** A module path pattern uses `**` somewhere other than as its last segment. */
    @InternalKatachiApi
    public object DoubleStarBeforeLastSegment : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` before its last segment. A module path may only use `**` " +
                "as its last segment, so that the index of each captured wildcard is the " +
                "same for every match."
    }

    /** A module path was written as an empty string. */
    @InternalKatachiApi
    public object EmptyModulePath : GlobProblem {
        override fun explain(pattern: String): String =
            "A module path must not be empty. Write `\":\"` for the root project."
    }

    /** Two `:` in a row, or a trailing one, in a module path. */
    @InternalKatachiApi
    public class EmptyModuleName internal constructor(
        public val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has an empty module name. Two `$separator` in a row, or a trailing " +
                "`$separator`, names no module."
    }

    /** A wildcard where exactly one module had to be named. */
    @InternalKatachiApi
    public class WildcardInModuleName internal constructor(
        public val moduleName: String,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `*` in the module name `$moduleName`. A module path names one " +
                "module; a pattern that matches several is expanded before this point."
    }

    /** A layout key with two `/` in a row, a leading `/`, or a trailing one. */
    @InternalKatachiApi
    public object EmptyLayoutKeyLevel : GlobProblem {
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
 */
@InternalKatachiApi
public sealed interface GlobContext {
    /** The sentence placed before the problem's own. */
    public fun describe(): String

    /** A path pattern written in the `layout { }` of one role. */
    @InternalKatachiApi
    public class RoleLayoutPath internal constructor(
        public val role: Role,
        public val path: String,
        public val declaredAt: DeclarationSite,
    ) : GlobContext {
        override fun describe(): String =
            "Role ${role.qualifiedName} declares the layout path `$path` at $declaredAt."
    }

    /** A module path written as a `"...".module { }` key. */
    @InternalKatachiApi
    public class LayoutModulePath internal constructor(
        public val key: String,
        public val declaredAt: DeclarationSite,
    ) : GlobContext {
        override fun describe(): String = "The layout declares the module `$key` at $declaredAt."
    }
}
