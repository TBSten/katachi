package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.internal.assertNoErrors
import me.tbsten.katachi.scan.Severity
import me.tbsten.katachi.scan.UncheckedCheck
import me.tbsten.katachi.scan.Violation

/**
 * Throws when this list holds a [Severity.Error], and hands the list back when it does not.
 *
 * A [Severity.Error] -> `throw KatachiArchitectureAssertionError`, carrying **every** violation
 * of this list, warnings included. Warnings only, or nothing at all -> this list, unchanged: a
 * warning is reported, never a reason to fail.
 *
 * **This is how a check written outside katachi says "I found something".** Call it last,
 * inside the `runCatching { }` that is the body of `process`: the throw becomes the check's
 * `Result.failure`, which is what lets `validate()` and `assert()` put the violations into the
 * one report instead of calling the check broken, and what makes `runKatachiProcessor` print
 * the same report and exit non-zero. The constructor of [KatachiArchitectureAssertionError] is
 * not public, and does not need to be: this is the one way in. Any other failure is a check
 * that could not run, and becomes one [UncheckedCheck] instead.
 *
 * ## Example 1: a check of your own that reports through the same report as the layout check
 * ```kt
 * import me.tbsten.katachi.check.assertNoErrors
 * import me.tbsten.katachi.processor.ArchitectureProcessContext
 * import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
 * import me.tbsten.katachi.scan.Severity
 * import me.tbsten.katachi.scan.Violation
 * import me.tbsten.katachi.scan.ViolationKind
 *
 * class TodoFile(override val path: String) : Violation {
 *     override val kind: ViolationKind = ViolationKind.Constraint
 *     override val severity: Severity = Severity.Error
 *     override val label: String = "TodoFile"
 * }
 *
 * object NoTodoFiles : ArchitectureProcessorNoArg<List<Violation>> {
 *     override fun process(context: ArchitectureProcessContext<Unit>): Result<List<Violation>> =
 *         runCatching {
 *             context.roles
 *                 .flatMap { context.filesOf(it) }
 *                 .filter { "TODO" in it }
 *                 .map { TodoFile(path = it) }
 *                 .assertNoErrors()
 *         }
 * }
 * ```
 *
 * @throws KatachiArchitectureAssertionError when any violation of this list is a
 *   [Severity.Error].
 */
@ExperimentalKatachiApi
public fun List<Violation>.assertNoErrors(): List<Violation> =
    // TODO: a third-party check has no project root to hand over here, so its message keeps the
    //  paths relative. An overload taking the `ArchitectureProcessContext` could resolve them.
    //  `validate()` / `assert()` rebuild the message with the root either way.
    assertNoErrors(projectRoot = null)
