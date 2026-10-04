package me.tbsten.katachi.konture.internal

import io.github.baole.konture.Konture
import io.github.baole.konture.OutputFormat
import io.github.baole.konture.core.KontureLogger

/**
 * The report path Konture holds while katachi audits: no `.html`, `.json` or `.sarif` suffix, so
 * none of Konture's report writers is enabled by it.
 *
 * Konture 1.0.1 enables its HTML writer whenever the thread's `reportPath` ends in `.html`, and
 * its default value does, so without this every `check()` would write
 * `build/reports/konture/konture-report.html` under the working directory.
 */
private const val NO_REPORT_PATH: String = "katachi-konture-audit"

/**
 * Runs [block] with Konture in audit mode on this thread, and puts Konture back afterwards.
 *
 * The switches, all thread local in Konture, all restored in `finally`:
 *
 * - `Konture.failOnSeverity = null`, so that a builder's `check()` returns its `ViolationReport`
 *   instead of throwing the violations as one `AssertionError`. This is the whole mechanism:
 *   katachi reads the report and makes one report block per violation.
 * - `Konture.generateBaseline = false`, so that katachi's run never writes Konture's
 *   `konture-baseline.json`.
 * - `Konture.reportPath` without a report suffix, `Konture.outputFormat = HUMAN`, and the three
 *   `htmlReportPath` / `jsonReportPath` / `sarifReportPath` at their defaults, so that `check()`
 *   writes none of Konture's HTML / JSON / SARIF reports. Each of those is a way Konture's
 *   writer decides it is on; Konture's own default report path ends in `.html`, which by itself
 *   turns the HTML writer on for every `check()`.
 * - `KontureLogger.logger` swallowing everything, because in audit mode Konture logs each
 *   violation as an `ERROR` line to stdout — noise next to katachi's own report, and
 *   `minLevel` cannot silence `ERROR`.
 *
 * A `konture.*` system property wins over all of these, because Konture reads the property
 * before the thread's state: `-Dkonture.baseline.generate=true` still records a baseline and
 * `-Dkonture.report.path=...html` still writes a report. Those are the reader's explicit asks.
 *
 * Reading `failOnSeverity` and writing it back leaves Konture's "overridden" flag set on this
 * thread, so the `konture.fail.on.severity` system property is no longer consulted here. The
 * value is the same one it would have read, so nothing observable changes.
 *
 * What is deliberately not put back: Konture's JVM wide bookkeeping. Each `check()` appends a
 * `RuleEvaluation` to Konture's report accumulator and the violations it saw to the baseline
 * manager's evaluated set, and each parsed file stays in the `IncrementalAstCache`; none of
 * them is bounded or evicted. The only way to drop them is `Konture.reset()`, which would also
 * wipe the Konture settings a reader keeps on this thread for Konture tests of their own, so
 * katachi leaves them to grow — by one evaluation per rule per run, and by one parse per
 * covered file per distinct covered set. `.local/v0.3/konture/design.md` 2.5 has the numbers.
 */
internal inline fun <T> audited(block: () -> T): T {
    val severity = Konture.failOnSeverity
    val baseline = Konture.generateBaseline
    val reportPath = Konture.reportPath
    val outputFormat = Konture.outputFormat
    val htmlReportPath = Konture.htmlReportPath
    val jsonReportPath = Konture.jsonReportPath
    val sarifReportPath = Konture.sarifReportPath
    val logger = KontureLogger.logger
    Konture.failOnSeverity = null
    Konture.generateBaseline = false
    Konture.reportPath = NO_REPORT_PATH
    Konture.outputFormat = OutputFormat.HUMAN
    Konture.htmlReportPath = Konture.DEFAULT_HTML_REPORT_PATH
    Konture.jsonReportPath = Konture.DEFAULT_JSON_REPORT_PATH
    Konture.sarifReportPath = Konture.DEFAULT_SARIF_REPORT_PATH
    KontureLogger.logger = { _, _, _ -> }
    try {
        return block()
    } finally {
        Konture.failOnSeverity = severity
        Konture.generateBaseline = baseline
        Konture.reportPath = reportPath
        Konture.outputFormat = outputFormat
        Konture.htmlReportPath = htmlReportPath
        Konture.jsonReportPath = jsonReportPath
        Konture.sarifReportPath = sarifReportPath
        KontureLogger.logger = logger
    }
}
