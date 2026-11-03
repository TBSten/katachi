package me.tbsten.katachi.konture.internal

import io.github.baole.konture.Konture
import io.github.baole.konture.OutputFormat
import io.github.baole.konture.core.KontureLogger

// No `.html`, `.json` or `.sarif` suffix: Konture enables its HTML writer whenever `reportPath` ends in `.html`, which its default does.
private const val NO_REPORT_PATH: String = "katachi-konture-audit"

// All thread local, restored in `finally`: `failOnSeverity = null` makes `check()` return the report; report writers, baseline generation and logging are off. `-Dkonture.*` still wins.
// Konture's JVM-wide bookkeeping is deliberately not restored: `Konture.reset()` would also wipe the reader's own thread settings.
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
