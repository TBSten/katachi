package me.tbsten.katachi.docs.internal

import java.io.File
import java.io.IOException
import me.tbsten.katachi.docs.KatachiDocumentIoException
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.displayExistingPath

// Removes pages in outputRoot that this run did not produce: the directory must belong to katachi alone.
internal fun writeDocuments(outputRoot: File, pages: Map<String, String>, log: (String) -> Unit) {
    val generated = pages.keys.mapTo(mutableSetOf()) { it.lowercase() }
    for (stale in existingPagesOf(outputRoot).filterNot { it.lowercase() in generated }) {
        val file = File(outputRoot, stale)
        catchingIo(stale, outputRoot) { if (!file.delete()) throw IOException("delete returned false") }
        log("Removed ${displayExistingPath(absolutePathOf(outputRoot.path), stale)}, which this definition no longer produces.")
    }
    for ((path, content) in pages) {
        val file = File(outputRoot, path)
        catchingIo(path, outputRoot) {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }
    // Reported after the writing rather than during it, as one tree instead of one line per
    // page. Twenty lines that each repeat their own directory are harder to read than the shape
    // they form -- see `outputTreeLines`. Removals stay on their own lines above, because a
    // deletion is the one thing here a reader has to notice.
    outputTreeLines(pages.keys).forEach(log)
    removeEmptyDirectories(outputRoot)
}

internal fun compareDocuments(outputRoot: File, pages: Map<String, String>): DocumentDifference {
    val missing = mutableListOf<String>()
    val different = mutableListOf<String>()
    for ((path, content) in pages) {
        val file = File(outputRoot, path)
        when {
            !file.isFile -> missing += path
            catchingIo(path, outputRoot) { file.readText() } != content -> different += path
        }
    }
    val generated = pages.keys.mapTo(mutableSetOf()) { it.lowercase() }
    return DocumentDifference(
        missing = missing,
        different = different,
        extra = existingPagesOf(outputRoot).filterNot { it.lowercase() in generated },
    )
}

internal class DocumentDifference(
    val missing: List<String>,
    val different: List<String>,
    val extra: List<String>,
) {
    val isUpToDate: Boolean get() = missing.isEmpty() && different.isEmpty() && extra.isEmpty()
}

private fun existingPagesOf(outputRoot: File): List<String> {
    if (!outputRoot.isDirectory) return emptyList()
    return outputRoot.walkTopDown()
        .filter { it.isFile && it.name.endsWith(PAGE_EXTENSION) }
        .map { it.toRelativeString(outputRoot).replace(File.separatorChar, '/') }
        .sorted()
        .toList()
}

// outputRoot itself stays: the user pointed outputDir at it.
private fun removeEmptyDirectories(outputRoot: File) {
    if (!outputRoot.isDirectory) return
    outputRoot.walkBottomUp()
        .filter { it != outputRoot && it.isDirectory }
        .forEach { it.delete() }
}

private fun <T> catchingIo(path: String, outputRoot: File, block: () -> T): T = try {
    block()
} catch (cause: IOException) {
    throw KatachiDocumentIoException(path = path, outputDir = outputRoot.path, cause = cause)
} catch (cause: SecurityException) {
    throw KatachiDocumentIoException(path = path, outputDir = outputRoot.path, cause = cause)
}
