package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.TemplateDetailModel
import java.nio.file.InvalidPathException
import java.nio.file.Path

/*
 * Where a template may write, as far as the JSON tells. Never shown as a list: the screen only
 * shows what katachiTemplate reports writing. Used for blocking a file without a target (E-27), for
 * telling the plugin's own writes from definition changes (E-44), and for the files that exist when
 * the output does not name what was written (E-21, E-37, E-42).
 */

/** Where an expected file lands, as far as the JSON tells. */
internal sealed interface ExpectedLocation {
    /** Relative to the project root, placeholders replaced as far as the inputs allow. */
    data class Known(val path: String) : ExpectedLocation

    /**
     * `path == null` in the JSON: a wildcard target. Blocks generation while checked (E-27).
     * [patterns] is the declared pattern, in a list for the same shape the old, per-role JSON gave.
     */
    data class Unresolved(val patterns: List<String>) : ExpectedLocation

    /** A file only a branch adds: the JSON has its name but not its path. */
    data object FromBranch : ExpectedLocation
}

/** One file the template may write, placeholders replaced by the current inputs. */
internal data class ExpectedFile(val fileName: String, val location: ExpectedLocation)

/**
 * The files the template may write with [inputs]: the preview's files in declaration order without
 * those a taken branch removes, then the files taken branches add (the JSON does not say where those
 * fall in the declaration order, E-10).
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
                ExpectedLocation.Unresolved(listOf(file.pattern))
            } else {
                ExpectedLocation.Known(expectedTextOf(path, detail, inputs))
            },
        )
    }
    val added = active.flatMap { it.addedFiles }.distinct().filter { it !in baseNames }.map { name ->
        ExpectedFile(expectedTextOf(name, detail, inputs), ExpectedLocation.FromBranch)
    }
    return base + added
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
