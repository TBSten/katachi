package me.tbsten.katachi.benchmark

import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.FileSelection
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.synthetic.SyntheticPlacement
import me.tbsten.katachi.test.synthetic.SyntheticProject

/** The seed every benchmark generates its project from, so two runs measure the same tree. */
internal const val BENCHMARK_SEED: Long = 42L

/**
 * [SyntheticProject]'s own `architecture()`, plus one `konsist { }` per role.
 *
 * The same layout, so the walk and its violations are the same; the constraint makes
 * `FileConstraintCheck` parse every `.kt` file a role owns. It asks something every generated
 * class satisfies, so the run reports no constraint violation and the time is the parse and
 * the slice, not a report.
 */
internal fun SyntheticProject.konsistArchitecture(): Architecture {
    val roles = roles
    return architecture {
        files = FileSelection.WholeTree
        "synthetic".group {
            for ((index, role) in roles.withIndex()) {
                role.name {
                    "classes are public".konsist {
                        classes().must { it.hasPublicOrDefaultModifier }
                    }
                    layout {
                        if (index == 0) {
                            ":".module {
                                description = "The root project."
                                "settings.gradle".ktsFile()
                            }
                        }
                        when (role.placement) {
                            SyntheticPlacement.Module -> ":modules:*".module {
                                description = "${role.name} of every module."
                                mainSourceSet / kotlin / role.directory / "*${role.name}".ktFile()
                            }

                            SyntheticPlacement.Fixed -> "shared/${role.directory}" {
                                "*${role.name}".ktFile()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A fresh, empty directory for [SyntheticProject.writeTo]. Delete it with [deleteProjectDirectory]. */
internal fun createProjectDirectory(): File =
    Files.createTempDirectory("katachi-benchmark-").toFile()

internal fun deleteProjectDirectory(directory: File) {
    directory.deleteRecursively()
}

/**
 * Fails the benchmark's setup unless [violations] are exactly [expected] (as `[label] path`).
 *
 * Run once before measuring, so a benchmark that stopped measuring what it claims — a layout
 * that no longer matches the generated tree, say — fails instead of timing an error path.
 */
internal fun requireViolations(violations: List<Violation>, expected: List<String>) {
    val actual = violations.map { "[${it.label}] ${it.path}" }.sorted()
    check(actual == expected.sorted()) {
        "The benchmark's project no longer checks as expected.\n" +
            "expected: ${expected.take(5)} (${expected.size})\n" +
            "actual:   ${actual.take(5)} (${actual.size})"
    }
}

/** What `assert()` does with the violations `validate()` returned: fail on any `Error`. */
internal fun assertNoErrors(violations: List<Violation>) {
    val errors = violations.filter { it.severity == Severity.Error }
    check(errors.isEmpty()) {
        "assert() would fail: ${errors.take(5).map { "[${it.label}] ${it.path}" }} (${errors.size})"
    }
}
