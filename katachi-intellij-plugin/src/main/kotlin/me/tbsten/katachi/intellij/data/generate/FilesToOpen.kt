package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.model.GenerationReport
import java.nio.file.Path

/** The "open after generation" setting. */
internal enum class OpenAfterGeneration { None, First, All }

/**
 * The files to open after [report] (E-46): by default the first written file, which is the first
 * template's first declared file, or the next written one when that template was skipped. Nothing
 * when nothing was written.
 */
internal fun filesToOpen(report: GenerationReport, setting: OpenAfterGeneration): List<Path> {
    val written = report.writtenFiles.map { it.path }
    return when (setting) {
        OpenAfterGeneration.None -> emptyList()
        OpenAfterGeneration.First -> written.take(1)
        OpenAfterGeneration.All -> written.distinct()
    }
}
