package com.example.sample.data.legacy

// Deliberately misplaced as the baseline() demo: legacy/ is held back in katachi-baseline.json,
// so moving or deleting this package turns that entry into a StaleBaselineEntry.
class LegacyUserCache {
    private var userName: String? = null

    fun get(): String? = userName

    fun put(value: String) {
        userName = value
    }
}
