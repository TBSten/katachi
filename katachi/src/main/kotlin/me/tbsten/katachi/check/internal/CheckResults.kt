package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.DEFAULT_MAX_VIOLATIONS
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.UncheckedCheck
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.internal.isFatal
import me.tbsten.katachi.processor.ArchitectureProcessor

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
        check = checkNameOf(check),
        cause = cause,
    )

/**
 * What a check is called in a report and in the baseline: its qualified class name, or its JVM
 * name when it has none (an anonymous or local class).
 */
internal fun checkNameOf(check: ArchitectureProcessor<*, *>): String =
    check::class.qualifiedName ?: check::class.java.name

/**
 * [assertNoErrors], with the message's paths resolved against [projectRoot].
 *
 * The message is cut to [DEFAULT_MAX_VIOLATIONS] and never reads `-Dkatachi.maxViolations`:
 * this is what katachi's own checks throw from inside a run, and `assertWith` takes the
 * violations off the throw and builds the message the user sees -- with the resolved budget --
 * itself. Reading the property here would print the same warning once per check.
 */
internal fun List<Violation>.assertNoErrors(projectRoot: FsPath?): List<Violation> {
    if (any { it.severity == Severity.Error }) {
        throw KatachiArchitectureAssertionError(this, DEFAULT_MAX_VIOLATIONS, projectRoot)
    }
    return this
}

/**
 * The public `assertNoErrors()`, with where it reads `-Dkatachi.maxViolations` from handed in.
 *
 * A check written outside katachi calls that one, and when it is called on its own -- not
 * inside `assert()` -- its message is what the user reads, so the budget is resolved here the
 * way `assertWith` resolves it. The paths stay relative: a third-party check has no project
 * root to resolve them against.
 */
internal fun List<Violation>.assertNoErrors(
    systemProperty: (String) -> String?,
    standardError: (String) -> Unit,
): List<Violation> {
    if (any { it.severity == Severity.Error }) {
        val budget = resolveMaxViolations(explicit = null, systemProperty, standardError)
        throw KatachiArchitectureAssertionError(this, budget, projectRoot = null)
    }
    return this
}
