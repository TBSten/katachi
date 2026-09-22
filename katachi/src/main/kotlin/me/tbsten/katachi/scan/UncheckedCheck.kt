package me.tbsten.katachi.scan

/**
 * A check handed to `assert(...)` that threw, so nothing it would have reported is known.
 *
 * The path is `"."`: a processor is about the project as a whole, and `"."` is already how
 * the walk names the project root when it is the root itself that could not be read.
 *
 * ## Example 1: list the checks a run could not finish
 * ```kt
 * projectArchitecture.validate(TodoCheck()).filterIsInstance<UncheckedCheck>().map { it.check }
 * ```
 */
public class UncheckedCheck internal constructor(
    /**
     * The processor's qualified class name, or its JVM name when a qualified name is not
     * available (an anonymous or local class).
     *
     * ## Example 1: list which checks a run could not finish
     * ```kt
     * projectArchitecture.validate(TodoCheck()).filterIsInstance<UncheckedCheck>().map { it.check }
     * ```
     */
    public val check: String,
    /**
     * What the check threw.
     *
     * ## Example 1: read what went wrong before reporting it as a bug
     * ```kt
     * projectArchitecture.validate(TodoCheck())
     *     .filterIsInstance<UncheckedCheck>()
     *     .forEach { println("${it.check}: ${it.cause}") }
     * ```
     */
    public val cause: Throwable,
) : Violation {
    override val path: String get() = "."
    override val kind: ViolationKind get() = ViolationKind.Failed
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UncheckedCheck"
    override fun toString(): String = "[$label] $path"
}
