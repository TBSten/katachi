package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.data.gradle.stripAnsi
import java.nio.file.Path

internal data class TemplateRunOutput(
    val projectRoot: Path?,
    val written: List<Path>,
    val overwritten: List<Path>,
    val skippedExisting: List<Path>,
    val reachedKatachi: Boolean,
    val status: TemplateRunStatus,
)

internal sealed interface TemplateRunStatus {
    data object Ok : TemplateRunStatus

    data class Failed(val body: List<String>) : TemplateRunStatus {
        val conflicting: List<Path>? get() = conflictingFilesOf(body)
    }

    data object Missing : TemplateRunStatus
}

private const val KEY = "template"
private const val LOG_PREFIX = "  [$KEY] "
private const val SUMMARY_LINE = "[3/3] Katachi processor run"
private val GENERATING = Regex("""^Generating \d+ files? under (\S+)$""")
private val WROTE = Regex("""^Wrote (file:\S+)$""")
private val OVERWRITING = Regex("""^Overwriting \d+ of \d+ files?: (.+)$""")
private val WROTE_NOTHING = Regex("""^Wrote nothing: \d+ of \d+ files? (?:is|are) already there \((.+)\)\.$""")
private val CONFLICT_HEAD = Regex("""^\d+ of \d+ generated files? (?:already exists?) under \S+:$""")

internal fun parseTemplateOutput(output: List<String>): TemplateRunOutput {
    val lines = output.map(::stripAnsi)
    var projectRoot: Path? = null
    val written = mutableListOf<Path>()
    val overwritten = mutableListOf<Path>()
    val skipped = mutableListOf<Path>()
    var reached = false
    var status: TemplateRunStatus = TemplateRunStatus.Missing

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        index++
        when {
            line.startsWith(LOG_PREFIX) -> {
                val message = line.removePrefix(LOG_PREFIX)
                GENERATING.matchEntire(message)?.let { projectRoot = pathOfFileUri(it.groupValues[1]) }
                WROTE.matchEntire(message)?.let { match -> pathOfFileUri(match.groupValues[1])?.let(written::add) }
                OVERWRITING.matchEntire(message)?.let { match -> overwritten += pathsOf(match.groupValues[1]) }
                WROTE_NOTHING.matchEntire(message)?.let { match -> skipped += pathsOf(match.groupValues[1]) }
            }
            line.startsWith(SUMMARY_LINE) -> reached = true
            line == "[OK] $KEY" -> status = TemplateRunStatus.Ok
            line == "[FAILED] $KEY" -> {
                val body = lines.drop(index).takeWhile { it.startsWith("  ") }.map { it.removePrefix("  ") }
                index += body.size
                status = TemplateRunStatus.Failed(body)
            }
        }
    }
    return TemplateRunOutput(projectRoot, written, overwritten, skipped, reached, status)
}

private fun pathsOf(uriList: String): List<Path> = splitUriList(uriList).mapNotNull(::pathOfFileUri)

private fun conflictingFilesOf(body: List<String>): List<Path>? {
    if (body.isEmpty() || !CONFLICT_HEAD.matches(body.first())) return null
    return body.drop(1).takeWhile { it.startsWith("  ") }.mapNotNull { pathOfFileUri(it.trim()) }
}
