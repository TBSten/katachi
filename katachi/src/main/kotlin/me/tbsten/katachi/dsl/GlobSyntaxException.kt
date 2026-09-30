package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.internal.GlobContext
import me.tbsten.katachi.dsl.internal.GlobProblem

/**
 * A glob pattern katachi cannot make sense of.
 *
 * The wording is `problem`'s, and `context` adds where the pattern was written when that is known.
 * Both are internal bookkeeping; [pattern][KatachiGlobSyntaxException.pattern] is the part a caller
 * can rely on.
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
 *
 * @property pattern the pattern as it was written.
 */
public class KatachiGlobSyntaxException internal constructor(
    public val pattern: String,
    internal val problem: GlobProblem,
    internal val context: GlobContext? = null,
) : KatachiDeclarationException(globSyntaxMessage(pattern, problem, context))

/** The context's sentence, when there is one, in front of the problem's own. */
private fun globSyntaxMessage(pattern: String, problem: GlobProblem, context: GlobContext?): String =
    if (context == null) problem.explain(pattern) else "${context.describe()} ${problem.explain(pattern)}"
