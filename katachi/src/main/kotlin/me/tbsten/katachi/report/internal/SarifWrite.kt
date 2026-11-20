package me.tbsten.katachi.report.internal

import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.causeLine
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.report.toSarif

// A failed write is said on stderr and swallowed: the report must neither turn a passing check red nor hide a
// failing one's message.
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
        val written = writeSarifText(target, violations.toSarif(context), environment.writeNewText)
        if (written != target) {
            environment.standardError(
                "Warning: ${fileUri(target.value)} already exists, so this run was written to " +
                    "${fileUri(written.value)} instead. Either another assert() of this run wrote it, in this JVM " +
                    "or in another test JVM, or an earlier run left it and nothing deleted it before this one. " +
                    "Upload every numbered file next to it too, or give each architecture test its own " +
                    "-D$SARIF_PATH_PROPERTY path.",
            )
        }
    }.onFailure { cause ->
        environment.standardError(
            "Warning: katachi could not write the SARIF report to ${fileUri(target.value)} : ${causeLine(cause)} . " +
                "The check itself is not affected.",
        )
    }
}

// Taken on the disk rather than counted in memory: the test JVMs of one Test task (forkEvery, maxParallelForks) cannot
// see each other's counts, and each would take the unnumbered name for its first write.
internal fun writeSarifText(file: FsPath, text: String, writeNewText: (FsPath, String) -> Boolean): FsPath =
    generateSequence(1) { it + 1 }.map { numberedSarifPath(file, it) }.first { writeNewText(it, text) }

internal fun numberedSarifPath(file: FsPath, n: Int): FsPath {
    if (n <= 1) return file
    val name = file.name
    val dot = name.lastIndexOf('.')
    val numbered = if (dot <= 0) "$name-$n" else name.substring(0, dot) + "-$n" + name.substring(dot)
    val parent = file.parent ?: return FsPath.of(numbered)
    return parent / numbered
}
