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
 * @param maxViolations how many violations the message spells out. [violations] holds them
 *   all either way.
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
 * @param maxViolations how many violations the message spells out. The rest are counted on
 *   the last line. This is about the message only — the check always looks at everything.
 * @throws KatachiArchitectureAssertionError when the check found anything that fails it.
 */
public fun Architecture.assert(maxViolations: Int = DEFAULT_MAX_VIOLATIONS): Unit =
    assert(RealFileSystem(), maxViolations = maxViolations)

/**
 * [assert] against [fileSystem]. See `validate(fileSystem)`.
 *
 * As with `assert(maxViolations)`, the overload taking checks runs those too; this one runs
 * [LayoutCheck] alone.
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
 *         projectArchitecture.assert(ConstraintCheck())
 *     }
 * }
 * ```
 *
 * ## Example 2: several checks, and a longer report
 * ```kt
 * projectArchitecture.assert(ConstraintCheck(), TodoCheck(), maxViolations = 20)
 * ```
 *
 * @param maxViolations how many violations the message spells out. The rest are counted on
 *   the last line. This is about the message only — the check always looks at everything.
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

/** What all four `assert` overloads are: [validateWith], then throw if anything is an error. */
private fun Architecture.assertWith(
    fileSystem: KatachiFileSystem,
    checks: List<ArchitectureProcessor<List<Violation>>>,
    maxViolations: Int,
) {
    val violations = validateWith(fileSystem, checks)
    // TODO(v0.1 step 5): once warnings exist, print them to standard error when nothing failed,
    //  and keep them in the failure message when something did.
    if (violations.none { it.severity == Severity.Error }) return
    throw KatachiArchitectureAssertionError(violations, maxViolations)
}
