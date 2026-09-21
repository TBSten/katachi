package me.tbsten.katachi.check

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture

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
 * @param maxViolations how many violations the message spells out. The rest are counted on
 *   the last line. This is about the message only — the check always looks at everything.
 * @throws KatachiArchitectureAssertionError when the check found anything that fails it.
 */
public fun Architecture.assert(maxViolations: Int = DEFAULT_MAX_VIOLATIONS): Unit =
    assert(RealFileSystem(), maxViolations)

/** [assert] against [fileSystem]. See `validate(fileSystem)`. */
@InternalKatachiApi
public fun Architecture.assert(
    fileSystem: KatachiFileSystem,
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
) {
    val violations = validate(fileSystem)
    // TODO(step 5): once warnings exist, print them to standard error when nothing failed,
    //  and keep them in the failure message when something did.
    if (violations.none { it.severity == Severity.Error }) return
    throw KatachiArchitectureAssertionError(violations, maxViolations)
}
