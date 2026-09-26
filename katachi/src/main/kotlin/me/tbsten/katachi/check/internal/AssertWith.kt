package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.DEFAULT_MAX_VIOLATIONS
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessor

/**
 * `assert()` against [fileSystem]. See `validate(fileSystem)`.
 *
 * As with `assert(maxViolations)`, the overload taking checks runs those too; this one runs
 * [me.tbsten.katachi.check.LayoutCheck] alone.
 *
 * ## Example 1: assert against a fake tree, capping how many violations the message spells out
 * ```kt
 * shouldThrow<KatachiArchitectureAssertionError> {
 *     definition.assert(fakeFileSystem, maxViolations = 5)
 * }
 * ```
 */
internal fun Architecture.assert(
    fileSystem: KatachiFileSystem,
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
): Unit = assertWith(fileSystem, emptyList(), maxViolations)

/**
 * `assert()` with more checks than the layout one, all on the same walk of the project.
 *
 * [me.tbsten.katachi.check.LayoutCheck] runs whether or not it is in the arguments, and passing
 * it anyway changes nothing — see `validate(check, vararg more)` for why, for what happens when
 * a check throws, and for the four throwables that are never swallowed.
 *
 * The first check is a separate parameter rather than part of the vararg so that this cannot
 * be reached by `assert()`, `assert(10)` or `assert(fileSystem)`: those three keep meaning
 * exactly what they meant before this overload existed.
 *
 * ## Example 1: run a check of your own against a tree that only exists in memory
 * ```kt
 * shouldThrow<KatachiArchitectureAssertionError> {
 *     definition.assert(fakeFileSystem, TodoCheck())
 * }
 * ```
 */
internal fun Architecture.assert(
    fileSystem: KatachiFileSystem,
    check: ArchitectureProcessor<Unit, List<Violation>>,
    vararg more: ArchitectureProcessor<Unit, List<Violation>>,
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
): Unit = assertWith(fileSystem, listOf(check) + more, maxViolations)

/**
 * What all four `assert` overloads are: [me.tbsten.katachi.check.internal.validateWith], then
 * throw if anything is an error.
 *
 * Nothing failed when there is no [Severity.Error] violation, even if there are warnings — so
 * there is no [KatachiArchitectureAssertionError] to carry them. Standard error is what is left:
 * `report()` already renders a Warning-only list as the Warning section alone (see `report`'s
 * own doc), so the same call that builds the failure message below builds this one too, and the
 * two can never say something different about the same run.
 */
internal fun Architecture.assertWith(
    fileSystem: KatachiFileSystem,
    checks: List<ArchitectureProcessor<Unit, List<Violation>>>,
    maxViolations: Int,
) {
    val (violations, projectRoot) = validateWithRoot(fileSystem, checks)
    if (violations.none { it.severity == Severity.Error }) {
        val warnings = violations.report(maxViolations, projectRoot)
        if (warnings.isNotEmpty()) System.err.println(warnings)
        return
    }
    throw KatachiArchitectureAssertionError(violations, maxViolations, projectRoot)
}
