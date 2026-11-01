package com.example.service

// Deliberately breaks the layout (not *Service.kt) as the baseline() demo. Renaming or deleting
// it turns its katachi-baseline.json entry into a [StaleBaselineEntry] failure.
class LegacyHealthCheck {
    fun isHealthy(): Boolean = true
}
