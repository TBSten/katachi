package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.report.internal.SarifEnvironment
import me.tbsten.katachi.report.internal.writeSarifIfRequested

internal fun Architecture.assert(
    fileSystem: KatachiFileSystem,
    maxViolations: Int? = null,
): Unit {
    assertWith(fileSystem, emptyList(), maxViolations)
}

internal fun Architecture.assert(
    fileSystem: KatachiFileSystem,
    check: ArchitectureProcessor<Unit, List<Violation>>,
    vararg more: ArchitectureProcessor<Unit, List<Violation>>,
    maxViolations: Int? = null,
): Unit {
    assertWith(fileSystem, listOf(check) + more, maxViolations)
}

internal fun Architecture.assertNoErrors(
    fileSystem: KatachiFileSystem,
    maxViolations: Int? = null,
): List<Violation> = assertWith(fileSystem, emptyList(), maxViolations)

internal fun Architecture.assertNoErrors(
    fileSystem: KatachiFileSystem,
    check: ArchitectureProcessor<Unit, List<Violation>>,
    vararg more: ArchitectureProcessor<Unit, List<Violation>>,
    maxViolations: Int? = null,
): List<Violation> = assertWith(fileSystem, listOf(check) + more, maxViolations)

internal fun Architecture.assertWith(
    fileSystem: KatachiFileSystem,
    checks: List<ArchitectureProcessor<Unit, List<Violation>>>,
    maxViolations: Int?,
    environment: BaselineEnvironment = BaselineEnvironment(),
    sarif: SarifEnvironment = SarifEnvironment(),
): List<Violation> {
    // Resolved here and nowhere deeper: this is the one place whose message reaches the user,
    // so the property is read, and any warning about it printed, once per `assert()`.
    val budget = resolveMaxViolations(maxViolations, environment.systemProperty, environment.standardError)
    val baseline = baseline
    // Read before the walk: a refused update on CI should not cost a whole run first.
    val mode = if (baseline == null) BaselineMode.Check else environment.mode()
    val result = validateWithRoot(fileSystem, checks)
    val projectRoot = result.projectRoot
    val outcome = baseline?.let {
        applyBaseline(it, projectRoot, result.ran, result.unattributed, result.baselineDeclared, mode, environment)
    }
    outcome?.notices?.forEach(environment.standardError)
    val violations = outcome?.violations ?: result.violations
    val trailer = outcome?.trailer.orEmpty()
    // Before the throw, so that a failing check still leaves a file for the CI to upload. Every
    // violation the baseline let through, warnings included, and never cut to `maxViolations`.
    writeSarifIfRequested(violations, projectRoot, this, fileSystem.workingDirectory, sarif)
    if (violations.none { it.severity == Severity.Error }) {
        val warnings = violations.report(budget, projectRoot)
        if (warnings.isNotEmpty()) environment.standardError(warnings)
        trailer.forEach(environment.standardError)
        return violations
    }
    throw KatachiArchitectureAssertionError(violations, budget, projectRoot, trailer)
}
