package me.tbsten.katachi.report.internal

import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.causeLine
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.report.toSarif

/**
 * What `assertWith` calls between the baseline and the throw: writes [violations] as SARIF to the
 * file `-Dkatachi.report.sarif` names, and does nothing when it names none.
 *
 * A failure to write is said on standard error and swallowed -- the report is a side effect of
 * the check, and a full disk must not turn a passing check red or hide a failing one's message.
 * Only what `catching` lets through is caught; the JVM giving up still ends the run.
 */
internal fun writeSarifIfRequested(
    violations: List<Violation>,
    projectRoot: FsPath,
    architecture: Architecture,
    workingDirectory: FsPath,
    environment: SarifEnvironment,
) {
    val requested = environment.systemProperty(SARIF_PATH_PROPERTY)?.trim().orEmpty()
    if (requested.isEmpty()) return
    val parsed = FsPath.of(requested)
    // Relative to the JVM's working directory, which under Gradle is the module being tested.
    val target = if (parsed.isAbsolute) parsed else workingDirectory / requested
    catching {
        val context = sarifContextOf(projectRoot, architecture, workingDirectory, environment)
        val written = writeSarifText(target, violations.toSarif(context), environment.runs, environment.writeText)
        if (written != target) {
            environment.standardError(
                "Warning: an earlier assert() in this JVM already wrote ${fileUri(target.value)} , so this run was " +
                    "written to ${fileUri(written.value)} instead. Give each architecture test its own " +
                    "-D$SARIF_PATH_PROPERTY path to keep them apart.",
            )
        }
    }.onFailure { cause ->
        environment.standardError(
            "Warning: katachi could not write the SARIF report to ${fileUri(target.value)} : ${causeLine(cause)} . " +
                "The check itself is not affected.",
        )
    }
}

/**
 * Writes [text] to [file] -- or, from the second write to the same [file] in this JVM on, to its
 * numbered sibling (`katachi-2.sarif`) -- and returns the path actually written.
 */
internal fun writeSarifText(
    file: FsPath,
    text: String,
    runs: SarifRuns,
    write: (FsPath, String) -> Unit,
): FsPath {
    val target = numberedSarifPath(file, runs.next(file))
    write(target, text)
    return target
}

/** [file] itself for the first write, `name-<n>.ext` next to it for the [n]th. */
internal fun numberedSarifPath(file: FsPath, n: Int): FsPath {
    if (n <= 1) return file
    val name = file.name
    val dot = name.lastIndexOf('.')
    val numbered = if (dot <= 0) "$name-$n" else name.substring(0, dot) + "-$n" + name.substring(dot)
    val parent = file.parent ?: return FsPath.of(numbered)
    return parent / numbered
}
