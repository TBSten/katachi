package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.model.GenerationReport
import java.nio.file.Path

internal enum class OpenAfterGeneration { None, First, All }

internal fun filesToOpen(report: GenerationReport, setting: OpenAfterGeneration): List<Path> {
    val written = report.writtenFiles.map { it.path }
    return when (setting) {
        OpenAfterGeneration.None -> emptyList()
        OpenAfterGeneration.First -> written.take(1)
        OpenAfterGeneration.All -> written.distinct()
    }
}
