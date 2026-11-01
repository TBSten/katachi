package com.example.sample.feature.home

// Deliberately misplaced as the baseline() demo: it is held back in katachi-baseline.json, so
// moving or deleting it turns that entry into a StaleBaselineEntry.
internal object HomeFormatter {
    fun visitCountLabel(count: Int): String = "Visit #$count"
}
