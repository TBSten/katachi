package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.internal.isFatal
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.scan.Severity
import me.tbsten.katachi.scan.UncheckedCheck
import me.tbsten.katachi.scan.Violation

/**
 * Throws when this list holds a [Severity.Error], and hands the list back when it does not.
 *
 * A [Severity.Error] -> `throw KatachiArchitectureAssertionError`, carrying **every** violation
 * of this list, warnings included. Warnings only, or nothing at all -> this list, unchanged: a
 * warning is reported, never a reason to fail.
 *
 * **This is how a check written outside katachi says "I found something".** Call it last,
 * inside the `runCatching { }` that is the body of `process`: the throw becomes the check's
 * `Result.failure`, which is what lets `validate()` and `assert()` put the violations into the
 * one report instead of calling the check broken, and what makes `runKatachiProcessor` print
 * the same report and exit non-zero. The constructor of [KatachiArchitectureAssertionError] is
 * not public, and does not need to be: this is the one way in. Any other failure is a check
 * that could not run, and becomes one [UncheckedCheck] instead.
 *
 * ## Example 1: a check of your own that reports through the same report as the layout check
 * ```kt
 * import me.tbsten.katachi.check.assertNoErrors
 * import me.tbsten.katachi.processor.ArchitectureProcessContext
 * import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
 * import me.tbsten.katachi.scan.Severity
 * import me.tbsten.katachi.scan.Violation
 * import me.tbsten.katachi.scan.ViolationKind
 *
 * class TodoFile(override val path: String) : Violation {
 *     override val kind: ViolationKind = ViolationKind.Constraint
 *     override val severity: Severity = Severity.Error
 *     override val label: String = "TodoFile"
 * }
 *
 * object NoTodoFiles : ArchitectureProcessorNoArg<List<Violation>> {
 *     override fun process(context: ArchitectureProcessContext<Unit>): Result<List<Violation>> =
 *         runCatching {
 *             context.roles
 *                 .flatMap { context.filesOf(it) }
 *                 .filter { "TODO" in it }
 *                 .map { TodoFile(path = it) }
 *                 .assertNoErrors()
 *         }
 * }
 * ```
 *
 * @throws KatachiArchitectureAssertionError when any violation of this list is a
 *   [Severity.Error].
 */
@ExperimentalKatachiApi
public fun List<Violation>.assertNoErrors(): List<Violation> {
    if (any { it.severity == Severity.Error }) {
        throw KatachiArchitectureAssertionError(this, DEFAULT_MAX_VIOLATIONS)
    }
    return this
}

internal fun Result<List<Violation>>.violationsOf(
    check: ArchitectureProcessor<*, *>,
): List<Violation> = fold(
    onSuccess = { it },
    onFailure = { cause ->
        when {
            cause is KatachiArchitectureAssertionError -> cause.violations
            cause.isFatal -> throw cause
            else -> listOf(uncheckedCheckOf(check, cause))
        }
    },
)

/** The one [UncheckedCheck] standing for [check], which could not answer because of [cause]. */
internal fun uncheckedCheckOf(check: ArchitectureProcessor<*, *>, cause: Throwable): UncheckedCheck =
    UncheckedCheck(
        check = check::class.qualifiedName ?: check::class.java.name,
        cause = cause,
    )
