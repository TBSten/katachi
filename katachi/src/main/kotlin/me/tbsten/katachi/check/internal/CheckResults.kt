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

/** [assertNoErrors], with the message's paths resolved against [projectRoot]. */
internal fun List<Violation>.assertNoErrors(projectRoot: FsPath?): List<Violation> {
    if (any { it.severity == Severity.Error }) {
        throw KatachiArchitectureAssertionError(this, DEFAULT_MAX_VIOLATIONS, projectRoot)
    }
    return this
}
