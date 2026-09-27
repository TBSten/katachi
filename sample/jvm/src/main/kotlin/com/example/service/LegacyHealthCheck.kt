package com.example.service

/**
 * The health check from before `HealthService` existed, left where it was when katachi was
 * adopted.
 *
 * Deliberately breaks the layout, as the demo of `baseline = baselineFile()`: the `Service`
 * role only allows `*Service.kt` in this package, so this file is an `[UnexpectedFile]`. It is
 * recorded in `katachi-baseline.json` and held back instead of failing the test. Renaming it to
 * `*Service.kt` (or deleting it) turns its entry stale until the baseline is pruned.
 */
class LegacyHealthCheck {
    fun isHealthy(): Boolean = true
}
