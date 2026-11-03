package me.tbsten.katachi.intellij.data.baseline

internal sealed interface BaselineReading {
    data object NotConfigured : BaselineReading

    data class Read(val ledger: BaselineLedger) : BaselineReading

    data object Missing : BaselineReading

    data class UnknownVersion(val version: Int?) : BaselineReading

    data class Unreadable(val reason: String) : BaselineReading
}

internal const val KNOWN_BASELINE_VERSION: Int = 1
