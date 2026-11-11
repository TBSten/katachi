package me.tbsten.katachi.test.report

import me.tbsten.katachi.check.DEFAULT_MAX_VIOLATIONS
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.report.SarifContext

/**
 * The text a failing `assert()` prints for these violations, for the specs of katachi's other modules.
 *
 * ## Example 1: in a spec of katachi-konsist
 * ```kt
 * projectArchitecture.validate(RealFileSystem(fixtureRoot)).reportForTest() shouldContain "must"
 * ```
 */
public fun List<Violation>.reportForTest(
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
    projectRoot: FsPath? = null,
): String = report(maxViolations, projectRoot)

/**
 * This context, asking [isFile] whether a regular file sits at a path instead of the disk.
 *
 * ## Example 1: write SARIF for files that exist only in a spec
 * ```kt
 * val context = SarifContext(projectRoot = FsPath.of("/w/repo"), toolVersion = "0.0.0-test").withIsFile { true }
 * violations.toSarif(context)
 * ```
 */
public fun SarifContext.withIsFile(isFile: (FsPath) -> Boolean): SarifContext =
    SarifContext(projectRoot, sourceRoots, repositoryPrefix, architectureDeclaredAt, toolVersion, isFile, readText, filesUnder)
