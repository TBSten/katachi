package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.internal.GlobContext
import me.tbsten.katachi.dsl.internal.GlobProblem

/**
 * Thrown when a glob pattern in the definition cannot be parsed, such as one with an unclosed `{`.
 *
 * The message says what is wrong with the pattern and, when known, where it was written.
 *
 * ## Example 1: catch a broken pattern and read back what was written
 * ```kt
 * shouldThrow<KatachiGlobSyntaxException> { NamePattern.compile("on{Click") }
 *     .pattern shouldBe "on{Click"
 * ```
 *
 * @property pattern the pattern as it was written.
 */
public class KatachiGlobSyntaxException internal constructor(
    public val pattern: String,
    internal val problem: GlobProblem,
    internal val context: GlobContext? = null,
) : KatachiDeclarationException(globSyntaxMessage(pattern, problem, context))

private fun globSyntaxMessage(pattern: String, problem: GlobProblem, context: GlobContext?): String =
    if (context == null) problem.explain(pattern) else "${context.describe()} ${problem.explain(pattern)}"
