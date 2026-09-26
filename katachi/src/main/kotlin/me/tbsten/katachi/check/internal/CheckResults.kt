package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.DEFAULT_MAX_VIOLATIONS
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.fs.FsPath
import me.tbsten.katachi.internal.isFatal
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.scan.Severity
import me.tbsten.katachi.scan.UncheckedCheck
import me.tbsten.katachi.scan.Violation

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

/** [assertNoErrors], with the message's paths resolved against [projectRoot]. */
internal fun List<Violation>.assertNoErrors(projectRoot: FsPath?): List<Violation> {
    if (any { it.severity == Severity.Error }) {
        throw KatachiArchitectureAssertionError(this, DEFAULT_MAX_VIOLATIONS, projectRoot)
    }
    return this
}
