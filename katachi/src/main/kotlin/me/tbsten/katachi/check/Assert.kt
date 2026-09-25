package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.fs.internal.RealFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.scan.Violation

/**
 * How many blocks `assert()` prints before it stops and counts the rest.
 *
 * ## Example 1: show twice as many blocks as `report()` shows by default
 * ```kt
 * projectArchitecture.validate().report(maxViolations = DEFAULT_MAX_VIOLATIONS * 2)
 * ```
 */
public const val DEFAULT_MAX_VIOLATIONS: Int = 10

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
 *     fun `the project matches its declaration`() {
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
 * @featured
 */
public fun Architecture.assert(maxViolations: Int = DEFAULT_MAX_VIOLATIONS): Unit =
    assertWith(RealFileSystem(), emptyList(), maxViolations)

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
 *     fun `the project matches its declaration`() {
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
    check: ArchitectureProcessor<Unit, List<Violation>>,
    vararg more: ArchitectureProcessor<Unit, List<Violation>>,
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
): Unit = assertWith(RealFileSystem(), listOf(check) + more, maxViolations)
