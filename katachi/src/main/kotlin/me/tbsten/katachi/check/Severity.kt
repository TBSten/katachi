package me.tbsten.katachi.check

/**
 * Whether a violation fails the check, or is only reported.
 *
 * ## Example 1: keep only the violations that fail the check
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * failure.violations.filter { it.severity == Severity.Error }
 * ```
 */
public enum class Severity {
    /** `assert()` throws because of it. */
    Error,

    /** Reported next to the errors, but never the reason a check fails. */
    Warning,
}
