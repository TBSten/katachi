package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.check.internal.reportWithTrailer
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessor

/**
 * How many blocks `assert()` prints before it stops and counts the rest.
 *
 * ## Example 1: show twice as many blocks as `assert()` shows by default
 * ```kt
 * projectArchitecture.assert(maxViolations = DEFAULT_MAX_VIOLATIONS * 2)
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
 *   [violations][KatachiArchitectureAssertionError.violations] holds every violation of the run
 *   either way, warnings included.
 * @param projectRoot what the message resolves each path against to print it as a `file:///...`
 *   URI. `null` leaves the paths relative to the project root, as
 *   [violations][KatachiArchitectureAssertionError.violations] carries them.
 */
public class KatachiArchitectureAssertionError internal constructor(
    /**
     * Every violation of the run, including the ones the message left out. With a baseline,
     * what it held back is not here, and its stale entries are.
     */
    public val violations: List<Violation>,
    maxViolations: Int,
    projectRoot: FsPath? = null,
    trailer: List<String> = emptyList(),
) : AssertionError(violations.reportWithTrailer(maxViolations, projectRoot, trailer))

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
public fun Architecture.assert(maxViolations: Int = DEFAULT_MAX_VIOLATIONS): Unit {
    assertWith(RealFileSystem(), emptyList(), maxViolations)
}

/**
 * [assert] with more checks than the layout one, all on the same walk of the project.
 *
 * [LayoutCheck] runs whether or not it is in the arguments, and passing it anyway changes
 * nothing: it is not run twice, so no violation is counted twice.
 *
 * A check that throws, or answers with a `Result.failure` other than a
 * [KatachiArchitectureAssertionError], does not end the run: it becomes one [UncheckedCheck]
 * naming the check and what it threw, and every other check still reports what it found.
 * `VirtualMachineError`, `LinkageError`, `InterruptedException` and `AssertionError` thrown out
 * of a check are never caught, and pass straight through.
 *
 * The first check is a separate parameter rather than part of the vararg so that `assert()` and
 * `assert(10)` never reach this overload.
 *
 * ## Example 1: run a check of your own alongside the layout check, in the one test
 * ```kt
 * class ProjectArchitectureTest {
 *     @Test
 *     fun `the project matches its declaration`() {
 *         projectArchitecture.assert(FileConstraintCheck())
 *     }
 * }
 * ```
 *
 * ## Example 2: several checks, and a longer report
 * ```kt
 * projectArchitecture.assert(FileConstraintCheck(), TodoCheck(), maxViolations = 20)
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
): Unit {
    assertWith(RealFileSystem(), listOf(check) + more, maxViolations)
}

/**
 * Like [assert], and also hands back every violation of the run, warnings included, when it does
 * not throw.
 *
 * It throws exactly where [assert] throws, with the same message, and prints the same warnings
 * to standard error. What it adds is the return value: every violation of the run, from the
 * same single walk of the project. A test that wants to fail on errors **and** look at the
 * warnings calls this once, instead of paying for the walk -- git, module discovery, every
 * constraint backend -- twice.
 *
 * The walk is not remembered between calls: each call looks at the project as it is now, so a
 * file added or removed since the last call is never missed.
 *
 * Keep [assert] for a test function written with an expression body
 * (`fun test() = projectArchitecture.assert()`): with this function there, the test would
 * return a `List`, and JUnit does not run a test method that returns a value.
 *
 * ## Example 1: fail on errors, then require that there are no warnings either
 * ```kt
 * class ProjectArchitectureTest {
 *     @Test
 *     fun `the project matches its declaration, warnings included`() {
 *         projectArchitecture.assertNoErrors(FileConstraintCheck()) shouldBe emptyList()
 *     }
 * }
 * ```
 *
 * @param maxViolations the combined budget the message's error blocks and warning blocks
 *   share, as for [assert].
 * @throws KatachiArchitectureAssertionError when the check found anything that fails it.
 * @return every violation of the run, sorted as the report sorts them: only warnings, or
 *   nothing, since any error throws instead.
 * @see assert
 * @see List.assertNoErrors
 */
@ExperimentalKatachiApi
public fun Architecture.assertNoErrors(maxViolations: Int = DEFAULT_MAX_VIOLATIONS): List<Violation> =
    assertWith(RealFileSystem(), emptyList(), maxViolations)

/**
 * `assertNoErrors()` with more checks than the layout one, all on the same walk of the project.
 *
 * [LayoutCheck] runs whether or not it is in the arguments, exactly as for
 * `assert(check, vararg more)`.
 *
 * ## Example 1: one walk for the constraints, the errors and the warnings
 * ```kt
 * val warnings = projectArchitecture.assertNoErrors(FileConstraintCheck(), TodoCheck())
 * warnings.map { it.label } shouldNotContain "AmbiguousLayout"
 * ```
 *
 * @param maxViolations the combined budget the message's error blocks and warning blocks
 *   share, as for [assert].
 * @throws KatachiArchitectureAssertionError when anything that ran found something that fails
 *   it, a check that threw included.
 * @return every violation of the run: only warnings, or nothing, since any error throws instead.
 */
@ExperimentalKatachiApi
public fun Architecture.assertNoErrors(
    check: ArchitectureProcessor<Unit, List<Violation>>,
    vararg more: ArchitectureProcessor<Unit, List<Violation>>,
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
): List<Violation> = assertWith(RealFileSystem(), listOf(check) + more, maxViolations)
