package me.tbsten.katachi.check

import me.tbsten.katachi.check.internal.ROOT_PATH

/**
 * A check handed to `assert(...)` that threw, so nothing it would have reported is known.
 *
 * Its path is always `"."`, the project root, since a check is about the project as a whole.
 *
 * ## Example 1: list the checks a run could not finish
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(TodoCheck())
 * }
 * failure.violations.filterIsInstance<UncheckedCheck>()
 *     .forEach { println("${it.check}: ${it.cause}") }
 * ```
 */
public class UncheckedCheck internal constructor(
    /**
     * The processor's qualified class name, or its JVM name when a qualified name is not
     * available (an anonymous or local class).
     */
    public val check: String,
    /** What the check threw. */
    public val cause: Throwable,
) : Violation {
    override val path: String get() = ROOT_PATH
    override val kind: ViolationKind get() = ViolationKind.Failed
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UncheckedCheck"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}
