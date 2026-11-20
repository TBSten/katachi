package me.tbsten.katachi.docs.internal

import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import me.tbsten.katachi.docs.KatachiDocumentIoException
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.displayExistingPath

internal fun outputRootOf(outputDir: String): Path = catchingIo(".", outputDir) { Path.of(outputDir) }

// Removes pages in outputRoot that this run did not produce: the directory must belong to katachi alone.
internal fun writeDocuments(outputRoot: Path, pages: Map<String, String>, log: (String) -> Unit) {
    for (stale in stalePagesOf(outputRoot, pages.keys)) {
        catchingIo(stale, outputRoot) { outputRoot.pageAt(stale).deleteExisting() }
        log("Removed ${displayExistingPath(absolutePathOf(outputRoot.toString()), stale)}, which this definition no longer produces.")
    }
    for ((path, content) in pages) {
        catchingIo(path, outputRoot) {
            val file = outputRoot.pageAt(path)
            file.parent?.createDirectories()
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

internal fun compareDocuments(outputRoot: Path, pages: Map<String, String>): DocumentDifference {
    val missing = mutableListOf<String>()
    val different = mutableListOf<String>()
    for ((path, content) in pages) {
        val file = outputRoot.pageAt(path)
        when {
            !file.isRegularFile() -> missing += path
            catchingIo(path, outputRoot) { file.readText() } != content -> different += path
        }
    }
    return DocumentDifference(
        missing = missing,
        different = different,
        extra = stalePagesOf(outputRoot, pages.keys),
    )
}

internal class DocumentDifference(
    val missing: List<String>,
    val different: List<String>,
    val extra: List<String>,
) {
    val isUpToDate: Boolean get() = missing.isEmpty() && different.isEmpty() && extra.isEmpty()
}

// A page on disk whose path differs from a generated one only in case is that same page on a case
// insensitive file system, and what a renamed group or role left behind on a case sensitive one.
// Only the file system can tell the two apart.
private fun stalePagesOf(outputRoot: Path, generated: Set<String>): List<String> {
    val generatedByLowercase = generated.associateBy { it.lowercase() }
    return existingPagesOf(outputRoot).filterNot { existing ->
        val counterpart = generatedByLowercase[existing.lowercase()] ?: return@filterNot false
        counterpart == existing || isSameFile(outputRoot, existing, counterpart)
    }
}

private fun isSameFile(outputRoot: Path, existing: String, generated: String): Boolean {
    val generatedFile = outputRoot.pageAt(generated)
    // Files.isSameFile throws for a path that names nothing.
    if (!generatedFile.exists()) return false
    return catchingIo(existing, outputRoot) {
        Files.isSameFile(outputRoot.pageAt(existing), generatedFile)
    }
}

private fun existingPagesOf(outputRoot: Path): List<String> {
    val directory = outputRoot.listedDirectory() ?: return emptyList()
    return catchingIo(".", outputRoot) {
        directory.walk(PathWalkOption.FOLLOW_LINKS)
            .filter { it.isRegularFile() && it.name.endsWith(PAGE_EXTENSION) }
            .map { it.relativeTo(directory).invariantSeparatorsPathString }
            .sorted()
            .toList()
    }
}

// outputRoot itself stays: the user pointed outputDir at it.
private fun removeEmptyDirectories(outputRoot: Path) {
    val directory = outputRoot.listedDirectory() ?: return
    val subdirectories = catchingIo(".", outputRoot) {
        directory.walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.FOLLOW_LINKS)
            .filter { it != directory && it.isDirectory() }
            .toList()
    }
    // Deepest first, so that a directory emptied by removing its children goes as well.
    for (subdirectory in subdirectories.asReversed()) {
        try {
            subdirectory.deleteExisting()
        } catch (_: IOException) {
            // Still holds something, so it stays.
        }
    }
}

// An empty outputDir is resolved the way java.io.File resolves it: as no directory, so nothing is
// listed or removed, and with the pages under the file system root. Path.of("") would be the
// working directory instead, whose .md files are not katachi's to remove.
private fun Path.pageAt(page: String): Path =
    if (isEmptyPath()) fileSystem.getPath(fileSystem.separator, page) else resolve(page)

private fun Path.listedDirectory(): Path? = takeIf { !it.isEmptyPath() && it.isDirectory() }

private fun Path.isEmptyPath(): Boolean = toString().isEmpty()

private fun <T> catchingIo(path: String, outputRoot: Path, block: () -> T): T =
    catchingIo(path, outputRoot.toString(), block)

private fun <T> catchingIo(path: String, outputDir: String, block: () -> T): T = try {
    block()
} catch (cause: IOException) {
    throw KatachiDocumentIoException(path = path, outputDir = outputDir, cause = cause)
} catch (cause: InvalidPathException) {
    throw KatachiDocumentIoException(path = path, outputDir = outputDir, cause = cause)
} catch (cause: SecurityException) {
    throw KatachiDocumentIoException(path = path, outputDir = outputDir, cause = cause)
}
