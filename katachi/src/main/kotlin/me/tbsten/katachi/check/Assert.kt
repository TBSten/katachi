package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.fs.RealFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.scan.Severity
import me.tbsten.katachi.scan.Violation

/**
 * The failure `assert()` throws.
 *
 * It extends [AssertionError] and nothing else, so JUnit 4, JUnit 5 and kotest all treat it
 * as a failed test without katachi depending on any of them.
 *
 * ## Example 1: Inspecting the violations after catching the failure
 *
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * val paths = failure.violations.map { it.path }
 * ```
 *
 * @param maxViolations the combined budget the message's error blocks and warning blocks share.
 *   [violations] holds every violation of the run either way, warnings included.
 */
public class KatachiArchitectureAssertionError internal constructor(
    /** Every violation of the run, including the ones the message left out. */
    public val violations: List<Violation>,
    maxViolations: Int,
) : AssertionError(violations.report(maxViolations))

/**
 * Checks the project against this definition and fails the calling test if anything is off.
 *
 * One call, one test: a report holding every violation costs an agent one run to read, while
 * a test per rule costs it one run per violation.
 *
 * ## Example 1: Writing the one test that checks the project
 *
 * Write one test that calls `assert()`. That is the only thing a project adopting katachi
 * has to write.
 *
 * ```kt
 * class ProjectArchitectureTest {
 *     @Test
 *     fun `構成が allow list に従っている`() {
 *         projectArchitecture.assert()
 *     }
 * }
 * ```
 *
 * The overload taking checks runs those on the same walk as well; this one runs [LayoutCheck]
 * alone.
 *
 * @param maxViolations the combined budget the message's error blocks and warning blocks
 *   share. The rest are counted on their section's own last line. This is about the message
 *   only — the check always looks at everything.
 * @throws KatachiArchitectureAssertionError when the check found anything that fails it.
 */
public fun Architecture.assert(maxViolations: Int = DEFAULT_MAX_VIOLATIONS): Unit =
    assert(RealFileSystem(), maxViolations = maxViolations)

/**
 * [assert] against [fileSystem]. See `validate(fileSystem)`.
 *
 * As with `assert(maxViolations)`, the overload taking checks runs those too; this one runs
 * [LayoutCheck] alone.
 *
 * ## Example 1: assert against a fake tree, capping how many violations the message spells out
 * ```kt
 * shouldThrow<KatachiArchitectureAssertionError> {
 *     definition.assert(fakeFileSystem, maxViolations = 5)
 * }
 * ```
 */
@InternalKatachiApi
public fun Architecture.assert(
    fileSystem: KatachiFileSystem,
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
): Unit = assertWith(fileSystem, emptyList(), maxViolations)

/**
 * [assert] with more checks than the layout one, all on the same walk of the project.
 *
 * [LayoutCheck] runs whether or not it is in the arguments, and passing it anyway changes
 * nothing — see `validate(check, vararg more)` for why, for what happens when a check throws,
 * and for the four throwables that are never swallowed.
 *
 * The first check is a separate parameter rather than part of the vararg so that this cannot
 * be reached by `assert()`, `assert(10)` or `assert(fileSystem)`: those three keep meaning
 * exactly what they meant before this overload existed.
 *
 * ## Example 1: run a check of your own alongside the layout check, in the one test
 * ```kt
 * class ProjectArchitectureTest {
 *     @Test
 *     fun `構成が allow list に従っている`() {
 *         projectArchitecture.assert(KonsistCheck())
 *     }
 * }
 * ```
 *
 * ## Example 2: several checks, and a longer report
 * ```kt
 * projectArchitecture.assert(KonsistCheck(), TodoCheck(), maxViolations = 20)
 * ```
 *
 * @param maxViolations the combined budget the message's error blocks and warning blocks
 *   share. The rest are counted on their section's own last line. This is about the message
 *   only — the check always looks at everything.
 * @throws KatachiArchitectureAssertionError when anything that ran found something that fails
 *   it, a check that threw included.
 */
@ExperimentalKatachiApi
public fun Architecture.assert(
    check: ArchitectureProcessor<List<Violation>>,
    vararg more: ArchitectureProcessor<List<Violation>>,
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
): Unit = assertWith(RealFileSystem(), listOf(check) + more, maxViolations)

/**
 * [assert] with checks, against [fileSystem]: the in-memory tree katachi's own specs use, plus
 * whatever checks the spec is about.
 *
 * ## Example 1: fail a spec on a check of your own, against a tree that only exists in memory
 * ```kt
 * shouldThrow<KatachiArchitectureAssertionError> {
 *     definition.assert(fakeFileSystem, TodoCheck())
 * }
 * ```
 */
@InternalKatachiApi
@ExperimentalKatachiApi
public fun Architecture.assert(
    fileSystem: KatachiFileSystem,
    check: ArchitectureProcessor<List<Violation>>,
    vararg more: ArchitectureProcessor<List<Violation>>,
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
): Unit = assertWith(fileSystem, listOf(check) + more, maxViolations)

/**
 * What all four `assert` overloads are: [validateWith], then throw if anything is an error.
 *
 * Nothing failed when there is no [Severity.Error] violation, even if there are warnings — so
 * there is no [KatachiArchitectureAssertionError] to carry them. Standard error is what is left:
 * `report()` already renders a Warning-only list as the Warning section alone (see `report`'s
 * own doc), so the same call that builds the failure message below builds this one too, and the
 * two can never say something different about the same run.
 */
private fun Architecture.assertWith(
    fileSystem: KatachiFileSystem,
    checks: List<ArchitectureProcessor<List<Violation>>>,
    maxViolations: Int,
) {
    val violations = validateWith(fileSystem, checks)
    if (violations.none { it.severity == Severity.Error }) {
        val warnings = violations.report(maxViolations)
        if (warnings.isNotEmpty()) System.err.println(warnings)
        return
    }
    throw KatachiArchitectureAssertionError(violations, maxViolations)
}
