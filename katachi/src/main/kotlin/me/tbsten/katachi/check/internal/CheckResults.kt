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

internal fun uncheckedCheckOf(check: ArchitectureProcessor<*, *>, cause: Throwable): UncheckedCheck =
    UncheckedCheck(
        check = checkNameOf(check),
        cause = cause,
    )

internal fun checkNameOf(check: ArchitectureProcessor<*, *>): String =
    check::class.qualifiedName ?: check::class.java.name

// Never reads -Dkatachi.maxViolations: assertWith builds the user-visible message, so reading it here would warn once per check.
internal fun List<Violation>.assertNoErrors(projectRoot: FsPath?): List<Violation> {
    if (any { it.severity == Severity.Error }) {
        throw KatachiArchitectureAssertionError(this, DEFAULT_MAX_VIOLATIONS, projectRoot)
    }
    return this
}

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
