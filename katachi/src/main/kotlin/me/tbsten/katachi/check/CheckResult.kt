package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.internal.assertNoErrors

/**
 * Throws when this list holds a [Severity.Error], and hands the list back when it does not.
 *
 * A [Severity.Error] -> `throw KatachiArchitectureAssertionError`, carrying **every** violation
 * of this list, warnings included. Warnings only, or nothing at all -> this list, unchanged: a
 * warning is reported, never a reason to fail.
 *
 * **This is how a check written outside katachi says "I found something".** Call it last, inside
 * the `runCatching { }` that is the body of `process`: the throw becomes the check's
 * `Result.failure`, so `assert()` and `assertNoErrors()` put the violations into the one report,
 * and a `katachi<Key>` task prints the same report and exits non-zero. Any other failure is a
 * check that could not run, and becomes one [UncheckedCheck] instead.
 *
 * The message is cut to `-Dkatachi.maxViolations` blocks when that is given, else to
 * [DEFAULT_MAX_VIOLATIONS], exactly as `assert()` cuts its own.
 *
 * ## Example 1: a check of your own that reports through the same report as the layout check
 * ```kt
 * import me.tbsten.katachi.check.assertNoErrors
 * import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
 * import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
 * import me.tbsten.katachi.check.Severity
 * import me.tbsten.katachi.check.Violation
 * import me.tbsten.katachi.check.ViolationKind
 *
 * class TodoFile(override val path: String) : Violation {
 *     override val kind: ViolationKind = ViolationKind.FileConstraint
 *     override val severity: Severity = Severity.Error
 *     override val label: String = "TodoFile"
 * }
 *
 * object NoTodoFiles : ArchitectureProcessorNoArg<List<Violation>> {
 *     override fun process(context: ArchitectureProcessNoArgContext): Result<List<Violation>> =
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
    assertNoErrors(systemProperty = System::getProperty, standardError = System.err::println)
