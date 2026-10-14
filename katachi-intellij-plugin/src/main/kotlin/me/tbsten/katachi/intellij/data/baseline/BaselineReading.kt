package me.tbsten.katachi.intellij.data.baseline

/**
 * What the plugin knows of a project's `katachi-baseline.json`, for the "no role holds this file" notification.
 * Only a [Read] one is trusted: anything else leaves the notification as it was before the plugin read baselines,
 * worded as a condition ("unless the baseline holds it back").
 *
 * ```kotlin
 * when (val reading = parseBaselineText(text)) {
 *     is BaselineReading.Read -> reading.ledger.holdsFile("src/Foo.kt")
 *     else -> false
 * }
 * ```
 */
internal sealed interface BaselineReading {
    /** `architecture { }` names no baseline: nothing holds anything back. */
    data object NotConfigured : BaselineReading

    /** The file was read, and its `"version"` is one this plugin knows. */
    data class Read(val ledger: BaselineLedger) : BaselineReading

    /** The file is not there. */
    data object Missing : BaselineReading

    /** A `"version"` this plugin does not know ([version] is the number written), so its entries may mean something else. */
    data class UnknownVersion(val version: Int?) : BaselineReading

    /** The file is not JSON, or not shaped like a baseline, or could not be read from disk. */
    data class Unreadable(val reason: String) : BaselineReading
}

/** The one `"version"` of the baseline format this plugin reads (katachi's `BASELINE_FORMAT_VERSION`). */
internal const val KNOWN_BASELINE_VERSION: Int = 1
