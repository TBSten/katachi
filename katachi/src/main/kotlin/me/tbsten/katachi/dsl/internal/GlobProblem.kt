package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role

internal const val GLOB_ESCAPABLE: String = "*\\{}?[],"

internal sealed interface GlobProblem {
    fun explain(pattern: String): String

    object EmptyPattern : GlobProblem {
        override fun explain(pattern: String): String = "A glob pattern must not be empty."
    }

    class EmptySegment(
        val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has an empty segment. Two `$separator` in a row, or a trailing " +
                "`$separator`, matches nothing."
    }

    object TrailingBackslash : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` ends a segment with `\\`. Write `\\\\` for a literal backslash."
    }

    class UnescapableCharacter(
        val character: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` escapes `$character`, which katachi does not treat as a " +
                "metacharacter. Only `$GLOB_ESCAPABLE` can be escaped."
    }

    class DoubleStarInsideSegment(
        val segment: String,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` as part of the segment `$segment`. `**` means " +
                "\"zero levels or more\" and only makes sense as a whole segment; " +
                "use a single `*` to match part of a name."
    }

    class UnclosedBracket(
        val segment: String,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` opens `[` in `$segment` and never closes it. `[...]` matches one " +
                "character and needs its `]`; close it, or write `\\[` for a literal `[`."
    }

    class UnclosedBrace(
        val segment: String,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` opens `{` in `$segment` and never closes it. `{a,b}` needs its `}`; " +
                "close it, or write `\\{` for a literal `{`."
    }

    class UnmatchedClosing(
        val character: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has a `$character` that closes nothing. Write `\\$character` for a " +
                "literal `$character`."
    }

    object EmptyCharacterClass : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has a `[...]` with no character in it, which can match nothing. List " +
                "at least one character, or write `\\[\\]` for the literal text."
    }

    class ReversedRange(
        val from: Char,
        val to: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has the range `$from-$to`, whose first character comes after its " +
                "last. Write it as `$to-$from`."
    }

    class SeparatorInBrackets(
        val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` puts `$separator` inside `[...]`. `[...]` matches one character " +
                "inside a single level and never the separator; write the levels apart instead."
    }

    object EmptyBraces : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has `{}`, which lists no alternative. Write at least one, such as " +
                "`{a,b}`, or `{,b}` for \"nothing or b\"."
    }

    object NestedBraces : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` opens `{` inside another `{...}`. Alternatives do not nest; write " +
                "them out in one list, such as `{a,b,c}`."
    }

    object DoubleStarInBraces : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` inside `{...}`. An alternative stays within one level; " +
                "write `**` as a level of its own."
    }

    class SeparatorInBraces(
        val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` puts `$separator` inside `{...}`. An alternative stays within one " +
                "level; write one layout key per alternative instead."
    }

    class UnusableSeparator(
        val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` is to be split on `$separator`, but katachi's glob already gives " +
                "`$separator` a meaning. Use a separator other than `$GLOB_ESCAPABLE`, or none."
    }

    class DoubleStarUsedTooOften(
        val count: Int,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` $count times. A module path may use `**` at " +
                "most once, as its last segment, so that the index of each captured " +
                "wildcard is the same for every match."
    }

    object DoubleStarBeforeLastSegment : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` before its last segment. A module path may only use `**` " +
                "as its last segment, so that the index of each captured wildcard is the " +
                "same for every match."
    }

    object EmptyModulePath : GlobProblem {
        override fun explain(pattern: String): String =
            "A module path must not be empty. Write `\":\"` for the root project."
    }

    class EmptyModuleName(
        val separator: Char,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has an empty module name. Two `$separator` in a row, or a trailing " +
                "`$separator`, names no module."
    }

    class WildcardInModuleName(
        val moduleName: String,
    ) : GlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `*` in the module name `$moduleName`. A module path names one " +
                "module; a pattern that matches several is expanded before this point."
    }

    object EmptyLayoutKeyLevel : GlobProblem {
        override fun explain(pattern: String): String =
            "The layout key `$pattern` has an empty level. Two `/` in a row, a leading `/`, or a " +
                "trailing `/` matches nothing; paths in `layout { }` are always relative to the " +
                "project root."
    }
}

// Layout blocks are deferred, so the stack no longer points at the written line; the context puts it back.
internal sealed interface GlobContext {
    fun describe(): String

    class RoleLayoutPath(
        val role: Role,
        val path: String,
        val declaredAt: DeclarationSite,
    ) : GlobContext {
        override fun describe(): String =
            "Role ${role.qualifiedName} declares the layout path `$path` at $declaredAt."
    }

    class LayoutModulePath(
        val key: String,
        val declaredAt: DeclarationSite,
    ) : GlobContext {
        override fun describe(): String = "The layout declares the module `$key` at $declaredAt."
    }
}
