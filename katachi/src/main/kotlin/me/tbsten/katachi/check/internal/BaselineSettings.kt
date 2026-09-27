package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.Severity

/*
 * The two baseline behaviours that were decided provisionally, each kept in one place so that a
 * different answer is a one-line change.
 */

/**
 * How a stale baseline entry is reported. [Severity.Error] makes the baseline a ratchet: a fixed
 * violation fails the run until its entry is pruned, so its path cannot be reused silently.
 * [Severity.Warning] would only mention it.
 */
internal val STALE_BASELINE_ENTRY_SEVERITY: Severity = Severity.Error

/**
 * Whether the baseline file has to be declared in a `layout { }` like any other file. `false`
 * would let an `[UnexpectedFile]` at the baseline's own path through without an entry.
 */
internal const val BASELINE_FILE_MUST_BE_DECLARED: Boolean = true
