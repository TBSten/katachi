package com.example.service

/**
 * A service that predates the rule that every `*Service` is public.
 *
 * Deliberately `internal`, as the demo of `baseline = baselineFile()`: the `Service` role's
 * `konsist { }` constraint "public であること" fails on it, and `katachi-baseline.json` records
 * that failure by role, constraint and declaration, so it is held back instead of failing the
 * test. Making it public turns its entry stale until the baseline is pruned.
 */
internal class LegacyStatusService {
    fun status(): String = "UP"
}
