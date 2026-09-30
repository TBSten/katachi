package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.internal.BaselineKey
import me.tbsten.katachi.check.internal.STALE_BASELINE_ENTRY_SEVERITY

/**
 * An entry of the baseline that holds back more violations than the project still has.
 *
 * Some of what the entry held back was fixed, so the entry is out of date. It fails the run
 * because an entry left behind would quietly let the next file placed at the same path through.
 * Re-running the architecture test with `-Dkatachi.baseline.prune=true` shrinks it; prune only
 * lowers counts and never records a new violation.
 *
 * ## Example 1: list the entries a fix made obsolete
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> { projectArchitecture.assert() }
 * failure.violations.filterIsInstance<StaleBaselineEntry>().map { "${it.path}: ${it.allowed} -> ${it.found}" }
 * ```
 *
 * @see me.tbsten.katachi.dsl.Baseline
 */
@ExperimentalKatachiApi
public class StaleBaselineEntry internal constructor(
    /** The path of the entry, relative to the project root. */
    override val path: String,
    /** The fully qualified class name of the check the entry belongs to. */
    public val check: String,
    /** The label of the violations the entry holds back, e.g. `UnexpectedFile`. */
    public val rule: String,
    /** How many violations the entry holds back. */
    public val allowed: Int,
    /** How many of them the project still has; `0` when all of them were fixed. */
    public val found: Int,
    /** The baseline file, relative to the project root. */
    public val baselinePath: String,
    internal val role: String? = null,
    internal val constraint: String? = null,
    internal val declaration: String? = null,
) : Violation {
    internal constructor(key: BaselineKey, allowed: Int, found: Int, baselinePath: String) : this(
        path = key.path,
        check = key.check,
        rule = key.rule,
        allowed = allowed,
        found = found,
        baselinePath = baselinePath,
        role = key.role,
        constraint = key.constraint,
        declaration = key.declaration,
    )

    override val kind: ViolationKind get() = ViolationKind.Stale
    override val severity: Severity get() = STALE_BASELINE_ENTRY_SEVERITY
    override val label: String get() = "StaleBaselineEntry"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}
