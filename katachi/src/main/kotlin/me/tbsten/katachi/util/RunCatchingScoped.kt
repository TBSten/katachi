package me.tbsten.katachi.util

import me.tbsten.katachi.util.internal.RunCatchingScopedController
import me.tbsten.katachi.util.internal.newRunCatchingScopedScope

/**
 * [runCatching], with a way to fail without stopping.
 *
 * Inside [block], [RunCatchingScopedScope.failure] records a failure and lets the block carry on,
 * so a check can go through every role and report all of what it found rather than the first.
 * [RunCatchingScopedScope.throw] ends the block on the spot, the way a plain `throw` would.
 *
 * What comes back:
 *
 * - [block] returned and nothing was recorded -> `success` with its value.
 * - [block] returned and something was recorded -> `failure` with a
 *   [KatachiMultipleFailuresException] holding every recorded failure as a suppressed
 *   exception, in the order they were recorded. One recorded failure is still wrapped, so a
 *   caller reads the same shape whatever the count.
 * - [block] called `throw(exception)` -> `failure` with that exception itself.
 * - [block] threw anything else -> `failure` with what it threw.
 *
 * In the last two, whatever was recorded before is dropped: the block did not get to the end, so
 * what it had found so far is not the whole answer, and the exception that stopped it is the one
 * worth reading.
 *
 * Like [runCatching], this catches every [Throwable] -- `CancellationException` and errors such as
 * `OutOfMemoryError` included. Keep it around work that reads and decides, not around a
 * suspension point whose cancellation has to reach the caller.
 *
 * ## Example 1: report every role without a summary, not only the first
 * ```kt
 * import me.tbsten.katachi.util.runCatchingScoped
 *
 * val summaries = mapOf("UseCase" to "One behaviour", "Repository" to null, "Entity" to null)
 *
 * val result: Result<Int> = runCatchingScoped {
 *     for ((role, summary) in summaries) {
 *         if (summary == null) failure(IllegalStateException("$role has no summary"))
 *     }
 *     summaries.size
 * }
 *
 * result.exceptionOrNull()!!.suppressed.map { it.message } shouldBe listOf(
 *     "Repository has no summary",
 *     "Entity has no summary",
 * )
 * ```
 */
public inline fun <R> runCatchingScoped(block: RunCatchingScopedScope.() -> R): Result<R> =
    runCatching {
        try {
            val scope = newRunCatchingScopedScope()
            block(scope)
                .also { scope.throwIfHasFailures() }
        } catch (e: RunCatchingScopedController.Threw) {
            throw e.exception
        }
    }

/**
 * What a [runCatchingScoped] block can do besides returning: record a failure and carry on, or
 * stop here.
 *
 * An interface so that the list of what was recorded, and the way the block is ended, are not
 * reachable from the block itself -- only the two words below are.
 *
 * ## Example 1: stop at the first thing that makes the rest meaningless
 * ```kt
 * import me.tbsten.katachi.util.runCatchingScoped
 *
 * val result: Result<String> = runCatchingScoped {
 *     val root = System.getenv("PROJECT_ROOT") ?: `throw`(IllegalStateException("PROJECT_ROOT is not set"))
 *     root.trimEnd('/')
 * }
 * ```
 */
public interface RunCatchingScopedScope {
    /**
     * Ends the block now, and makes [exception] the failure [runCatchingScoped] returns.
     *
     * Anything recorded with [failure] before this is dropped.
     *
     * ## Example 1: give up when there is nothing to go on
     * ```kt
     * import me.tbsten.katachi.util.runCatchingScoped
     *
     * val result = runCatchingScoped<Int> {
     *     val roles = emptyList<String>()
     *     if (roles.isEmpty()) `throw`(IllegalStateException("no roles to check"))
     *     roles.size
     * }
     * result.exceptionOrNull()?.message shouldBe "no roles to check"
     * ```
     */
    public fun `throw`(exception: Throwable): Nothing

    /**
     * Records [exception] and lets the block carry on.
     *
     * ## Example 1: note a problem and keep going
     * ```kt
     * import me.tbsten.katachi.util.runCatchingScoped
     *
     * val result = runCatchingScoped {
     *     failure(IllegalStateException("UseCase has no summary"))
     *     "checked every role"
     * }
     * result.isFailure shouldBe true
     * ```
     */
    public fun failure(exception: Throwable)
}

/**
 * Every failure a [runCatchingScoped] block recorded with [RunCatchingScopedScope.failure], as one.
 *
 * The recorded failures are its suppressed exceptions, in the order they were recorded, so
 * `suppressed` is where to read them from -- a stack trace prints each of them under this one.
 * Only [runCatchingScoped] creates it.
 *
 * ## Example 1: read what a block recorded
 * ```kt
 * import me.tbsten.katachi.util.KatachiMultipleFailuresException
 * import me.tbsten.katachi.util.runCatchingScoped
 *
 * val failure = runCatchingScoped {
 *     failure(IllegalStateException("UseCase has no summary"))
 *     failure(IllegalStateException("Entity has no summary"))
 * }.exceptionOrNull()
 *
 * (failure as KatachiMultipleFailuresException).suppressed.size shouldBe 2
 * ```
 */
public class KatachiMultipleFailuresException internal constructor(
    exceptions: List<Throwable>,
) : RuntimeException() {
    init {
        exceptions
            .forEach(::addSuppressed)
    }
}
