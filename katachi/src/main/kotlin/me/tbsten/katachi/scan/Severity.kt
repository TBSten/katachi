package me.tbsten.katachi.scan

/**
 * Whether a violation fails the check, or is only reported.
 *
 * ## Example 1: keep only the violations that fail the check
 * ```kt
 * projectArchitecture.validate().filter { it.severity == Severity.Error }
 * ```
 */
public enum class Severity {
    /**
     * `assert()` throws because of it.
     *
     * ## Example 1: count how many violations fail the check
     * ```kt
     * projectArchitecture.validate().count { it.severity == Severity.Error }
     * ```
     */
    Error,

    /**
     * Reported next to the errors, but never the reason a check fails.
     *
     * ## Example 1: check whether a violation is only informational
     * ```kt
     * projectArchitecture.validate().first().severity == Severity.Warning
     * ```
     */
    Warning,
}
