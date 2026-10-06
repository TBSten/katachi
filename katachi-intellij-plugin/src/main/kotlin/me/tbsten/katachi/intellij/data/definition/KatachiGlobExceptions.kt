package me.tbsten.katachi.intellij.data.definition

/** Characters that mean themselves once written as `\<char>`. */
internal const val GLOB_ESCAPABLE: String = "*\\{}?[],"

/**
 * A glob pattern of the definition JSON that [KatachiGlob] cannot read. katachi only writes patterns
 * it compiled itself, so this means the plugin's port and katachi's `Glob` disagree (the conformance
 * test pins them together). The matcher drops the entry and keeps going.
 *
 * ```kotlin
 * val failure = runCatching { KatachiGlob.compile("Foo[.kt") }.exceptionOrNull() as? KatachiGlobSyntaxException
 * ```
 */
internal class KatachiGlobSyntaxException(
    val pattern: String,
    val problem: KatachiGlobProblem,
) : IllegalArgumentException(
    "Could not read the glob pattern `$pattern`: ${problem.explain(pattern)}\n" +
        "This plugin and the project's katachi disagree on the glob syntax. " +
        "Use a katachi version and a plugin version released together.",
)

/** Why a pattern could not be read. One subtype per thing that can go wrong, as in katachi's `GlobProblem`. */
internal sealed interface KatachiGlobProblem {
    fun explain(pattern: String): String

    data object EmptyPattern : KatachiGlobProblem {
        override fun explain(pattern: String): String = "A glob pattern must not be empty."
    }

    /** Two separators in a row, or a trailing one. */
    data class EmptySegment(val separator: Char) : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has an empty segment. Two `$separator` in a row, or a trailing `$separator`, matches nothing."
    }

    data object TrailingBackslash : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` ends a segment with `\\`. Write `\\\\` for a literal backslash."
    }

    data class UnescapableCharacter(val character: Char) : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` escapes `$character`, which katachi does not treat as a metacharacter. Only `$GLOB_ESCAPABLE` can be escaped."
    }

    data class DoubleStarInsideSegment(val segment: String) : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` as part of the segment `$segment`. `**` only makes sense as a whole segment."
    }

    data class UnclosedBracket(val segment: String) : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` opens `[` in `$segment` and never closes it. `[...]` matches one character and needs its `]`; " +
                "close it, or write `\\[` for a literal `[`."
    }

    data class UnclosedBrace(val segment: String) : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` opens `{` in `$segment` and never closes it. `{a,b}` needs its `}`; " +
                "close it, or write `\\{` for a literal `{`."
    }

    data class UnmatchedClosing(val character: Char) : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has a `$character` that closes nothing. Write `\\$character` for a literal `$character`."
    }

    data object EmptyCharacterClass : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has a `[...]` with no character in it, which can match nothing. " +
                "List at least one character, or write `\\[\\]` for the literal text."
    }

    data class ReversedRange(val from: Char, val to: Char) : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has the range `$from-$to`, whose first character comes after its last. Write it as `$to-$from`."
    }

    data class SeparatorInBrackets(val separator: Char) : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` puts `$separator` inside `[...]`. `[...]` matches one character inside a single level and " +
                "never the separator; write the levels apart instead."
    }

    data object EmptyBraces : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` has `{}`, which lists no alternative. Write at least one, such as `{a,b}`, " +
                "or `{,b}` for \"nothing or b\"."
    }

    data object NestedBraces : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` opens `{` inside another `{...}`. Alternatives do not nest; write them out in one list, " +
                "such as `{a,b,c}`."
    }

    data object DoubleStarInBraces : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `**` inside `{...}`. An alternative stays within one level; write `**` as a level of its own."
    }

    data class SeparatorInBraces(val separator: Char) : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` puts `$separator` inside `{...}`. An alternative stays within one level; " +
                "write one layout key per alternative instead."
    }
}
