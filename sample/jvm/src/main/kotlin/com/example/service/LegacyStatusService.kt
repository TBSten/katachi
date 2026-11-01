package com.example.service

// Deliberately internal, against the Service role's ktFileContent, as the baseline() demo.
// Making it public turns its katachi-baseline.json entry into a [StaleBaselineEntry] failure.
internal class LegacyStatusService {
    fun status(): String = "UP"
}
