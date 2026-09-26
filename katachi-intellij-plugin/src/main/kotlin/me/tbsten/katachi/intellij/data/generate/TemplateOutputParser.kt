package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.data.gradle.stripAnsi
import java.nio.file.Path

/** What `katachiTemplate`'s output says about one run, before the exit code is taken into account. */
internal data class TemplateRunOutput(
    /** From `Generating N files under <root>`: the project root katachi resolved. */
    val projectRoot: Path?,
    /** Every `Wrote <URI>`, in output order. */
    val written: List<Path>,
    /** From `Overwriting N of M files: …`: which of [written] replaced a file. */
    val overwritten: List<Path>,
    /** From `Wrote nothing: … (…).`: `onExisting=skip` found these and wrote nothing. */
    val skippedExisting: List<Path>,
    /** `[3/3] Katachi processor run` was printed: the processor ran. */
    val reachedKatachi: Boolean,
    val status: TemplateRunStatus,
)

internal sealed interface TemplateRunStatus {
    /** `[OK] template`. */
    data object Ok : TemplateRunStatus

    /** `[FAILED] template` with its indented body, dedented by two spaces. */
    data class Failed(val body: List<String>) : TemplateRunStatus {
        /** The files an `onExisting=fail` run found, when that is why it failed (E-17). */
        val conflicting: List<Path>? get() = conflictingFilesOf(body)
    }

    /** Neither summary line came (the run failed before katachi, or the output was cut, E-37). */
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

/**
 * Reads the lines of `katachiTemplate`'s output that the contract fixes (plan "`katachiTemplate`
 * の出力で IDE が読む行"): only lines starting with `  [template] `, the `[OK]` / `[FAILED]` summary
 * of the `template` key, and the body under `[FAILED]`. ANSI colors are dropped first.
 */
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

/**
 * The existing files of a conflict body: its first line is `N of M generated files already exist
 * under <root>:`, followed by indented `file:` URIs. `null` for any other failure.
 */
private fun conflictingFilesOf(body: List<String>): List<Path>? {
    if (body.isEmpty() || !CONFLICT_HEAD.matches(body.first())) return null
    return body.drop(1).takeWhile { it.startsWith("  ") }.mapNotNull { pathOfFileUri(it.trim()) }
}
