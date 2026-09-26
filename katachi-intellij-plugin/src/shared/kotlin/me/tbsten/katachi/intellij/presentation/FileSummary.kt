package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.TemplateDetailModel
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** Where an expected file lands, as far as the JSON tells. */
internal sealed interface ExpectedLocation {
    /** Relative to the project root, placeholders replaced as far as the inputs allow. */
    data class Known(val path: String) : ExpectedLocation

    /** `path == null` in the JSON: a wildcard target. Blocks generation while checked (E-27). */
    data class Unresolved(val patterns: List<String>) : ExpectedLocation

    /** A file only a branch adds: the JSON has its name but not its path. */
    data object FromBranch : ExpectedLocation
}

/** One file of the expected-file list, placeholders replaced by the current inputs. */
internal data class ExpectedFile(
    val fileName: String,
    val location: ExpectedLocation,
    /** The raw preview, placeholders replaced; `null` for a file only a branch adds. */
    val content: String?,
) {
    /** The directory part of a known path with a trailing `/`, `""` at the root, `null` otherwise. */
    val directory: String?
        get() = (location as? ExpectedLocation.Known)?.path?.let { path ->
            val slash = path.lastIndexOf('/')
            if (slash < 0) "" else path.substring(0, slash + 1)
        }
}

/**
 * The files the template is expected to produce with [inputs]: the preview's files in declaration
 * order without those a taken branch removes, then the files taken branches add (the JSON does not
 * say where those fall in the declaration order, E-10).
 */
internal fun expectedFilesOf(detail: TemplateDetailModel, inputs: Map<String, String>): List<ExpectedFile> {
    val active = activeBranchesOf(detail, inputs)
    val removed = active.flatMap { it.removedFiles }.toSet()
    val baseNames = detail.files.map { it.fileName }.toSet()
    val base = detail.files.filter { it.fileName !in removed }.map { file ->
        val path = file.path
        ExpectedFile(
            fileName = expectedTextOf(file.fileName, detail, inputs),
            location = if (path == null) {
                ExpectedLocation.Unresolved(file.unresolvedPatterns)
            } else {
                ExpectedLocation.Known(expectedTextOf(path, detail, inputs))
            },
            content = expectedTextOf(file.content, detail, inputs),
        )
    }
    val added = active.flatMap { it.addedFiles }.distinct().filter { it !in baseNames }.map { name ->
        ExpectedFile(expectedTextOf(name, detail, inputs), ExpectedLocation.FromBranch, content = null)
    }
    return base + added
}

/** The one-line summary under a form: "A.kt", or "A.kt and N more files", with ⚠ for a wildcard. */
internal data class FileSummary(
    /** The first file still expected, in declaration order; `null` when none is. */
    val firstFileName: String?,
    val otherCount: Int,
    val hasUnresolved: Boolean,
)

internal fun fileSummaryOf(files: List<ExpectedFile>): FileSummary = FileSummary(
    firstFileName = files.firstOrNull()?.fileName,
    otherCount = (files.size - 1).coerceAtLeast(0),
    hasUnresolved = files.any { it.location is ExpectedLocation.Unresolved },
)

/**
 * [directory] shortened in the middle for the narrow list: `data/src/main/kotlin/com/example/` →
 * `data/src/…/example/`. The tooltip shows it whole.
 */
internal fun abbreviateDirectory(directory: String, keepHead: Int = 2, keepTail: Int = 1): String {
    val trailingSlash = directory.endsWith("/")
    val segments = directory.trimEnd('/').split('/').filter { it.isNotEmpty() }
    if (segments.size <= keepHead + keepTail + 1) return directory
    val shortened = segments.take(keepHead) + "…" + segments.takeLast(keepTail)
    return shortened.joinToString("/") + if (trailingSlash) "/" else ""
}

/**
 * [path] (a [ExpectedLocation.Known] path) under [root], or `null` when this file system cannot name
 * it: the inputs are typed into the path, and Windows refuses `* ? : < > " |` in it.
 */
internal fun resolveExpectedPath(root: Path, path: String): Path? = try {
    root.resolve(path)
} catch (_: InvalidPathException) {
    null
}
