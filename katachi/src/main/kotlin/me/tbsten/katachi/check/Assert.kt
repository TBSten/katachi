package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.InternalKatachiApi

/**
 * The failure `assert()` throws.
 *
 * It extends [AssertionError] and nothing else, so JUnit 4, JUnit 5 and kotest all treat it
 * as a failed test without katachi depending on any of them.
 */
public class ArchitectureAssertionError internal constructor(
    /** Every violation of the run, including the ones the message left out. */
    public val violations: List<Violation>,
    message: String,
) : AssertionError(message)

/**
 * Checks the project against this definition and fails the calling test if anything is off.
 *
 * ```kotlin
 * class ProjectArchitectureTest {
 *   @Test fun `構成が allow list に従っている`() = projectArchitecture.assert()
 * }
 * ```
 *
 * One call, one test: a report holding every violation costs an agent one run to read, while
 * a test per rule costs it one run per violation.
 *
 * @param maxViolations how many violations the message spells out. The rest are counted on
 *   the last line. This is about the message only — the check always looks at everything.
 * @throws ArchitectureAssertionError when the check found anything that fails it.
 */
@OptIn(InternalKatachiApi::class)
public fun Architecture.assert(maxViolations: Int = DEFAULT_MAX_VIOLATIONS): Unit =
    assert(RealFileSystem(), maxViolations)

/** [assert] against [fileSystem]. See `validate(fileSystem)`. */
@OptIn(InternalKatachiApi::class)
@InternalKatachiApi
public fun Architecture.assert(
    fileSystem: KatachiFileSystem,
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
) {
    val violations = validate(fileSystem)
    // TODO(step 5): once warnings exist, print them to standard error when nothing failed,
    //  and keep them in the failure message when something did.
    if (violations.none { it.severity == Severity.Error }) return
    throw ArchitectureAssertionError(violations, violations.report(maxViolations))
}
