package me.tbsten.katachi.intellij.data.definition

/** Characters that mean themselves once written as `\<char>`. */
internal const val GLOB_ESCAPABLE: String = "*\\{}?[],"

/**
 * A glob pattern of the definition JSON that [KatachiGlob] cannot read. katachi only writes patterns
 * it compiled itself, so this means the plugin's port and katachi's `Glob` disagree (the conformance
 * test pins them together). The matcher drops the entry and keeps going.
 *
 * ```kotlin
 * val failure = runCatching { KatachiGlob.compile("Foo?.kt") }.exceptionOrNull() as? KatachiGlobSyntaxException
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

    /** A metacharacter of another tool's glob (`{ } ? [ ]`), refused rather than read as a literal. */
    data class RejectedMetacharacter(val character: Char) : KatachiGlobProblem {
        override fun explain(pattern: String): String =
            "`$pattern` uses `$character`. katachi's glob has only `*` and `**`; write `\\$character` for a literal `$character`."
    }
}
