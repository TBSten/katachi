package me.tbsten.katachi.intellij.data.sarif

import me.tbsten.katachi.intellij.data.watch.REPORTS
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

private val SARIF_NAME = Regex("""katachi(-\d+)?\.sarif""")

/**
 * The SARIF files katachi's `report { sarif = true }` left in [moduleDirectory] (design 4.7):
 * `build/reports/katachi/katachi.sarif`, `build/reports/katachi/<taskName>/katachi.sarif`, and next to
 * either the `katachi-<n>.sarif` of a second run in the same JVM. In path order; none when the directory
 * is missing. Read with `java.nio`, as Gradle writes behind the IDE's back.
 *
 * ```kotlin
 * val files = sarifFilesOf(module.directory)
 * ```
 */
internal fun sarifFilesOf(moduleDirectory: Path): List<Path> {
    val reports = moduleDirectory.resolve(REPORTS)
    if (!Files.isDirectory(reports)) return emptyList()
    return try {
        val directories = listOf(reports) + list(reports).filter { Files.isDirectory(it) }
        directories.flatMap { directory -> list(directory).filter { Files.isRegularFile(it) && SARIF_NAME.matches(it.fileName.toString()) } }.sorted()
    } catch (_: IOException) {
        emptyList()
    } catch (_: UncheckedIOException) {
        // `Files.list` iterates lazily: a directory deleted by a running check fails while it is walked.
        emptyList()
    }
}

private fun list(directory: Path): List<Path> = Files.list(directory).use { it.toList() }
