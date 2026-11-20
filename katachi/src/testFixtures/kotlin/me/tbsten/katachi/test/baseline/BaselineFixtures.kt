package me.tbsten.katachi.test.baseline

import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.BASELINE_UPDATE_PROPERTY
import me.tbsten.katachi.check.internal.BaselineEnvironment
import me.tbsten.katachi.check.internal.BaselineRuns
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessor

/**
 * `assert(checks)` against the baseline the architecture declares, for the specs of katachi's
 * other modules: with [update], it records the baseline as `-Dkatachi.baseline.update=true` would.
 *
 * Unlike `assert()`, it reads neither system properties nor the `CI` variable, and each call
 * forgets the runs before it, so one spec can record, change a file and check again in one JVM.
 *
 * ## Example 1: record, break something new, and check again
 * ```kt
 * projectArchitecture.assertWithBaselineForTest(RealFileSystem(root), FileConstraintCheck(), update = true)
 * File(root, "src/Models.kt").writeText(changed)
 * shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assertWithBaselineForTest(RealFileSystem(root), FileConstraintCheck())
 * }
 * ```
 */
public fun Architecture.assertWithBaselineForTest(
    fileSystem: KatachiFileSystem,
    vararg checks: ArchitectureProcessor<Unit, List<Violation>>,
    update: Boolean = false,
): List<Violation> = assertWith(
    fileSystem = fileSystem,
    checks = checks.toList(),
    maxViolations = null,
    environment = BaselineEnvironment(
        systemProperty = { key -> if (update && key == BASELINE_UPDATE_PROPERTY) "true" else null },
        environmentVariable = { null },
        runs = BaselineRuns(),
    ),
)
