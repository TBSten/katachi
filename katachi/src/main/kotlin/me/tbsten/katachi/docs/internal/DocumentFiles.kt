package me.tbsten.katachi.docs.internal

import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
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
    for (stale in stalePagesOf(outputRoot, existingPagesOf(outputRoot), pages.keys)) {
        catchingIo(stale, outputRoot) { outputRoot.pageAt(stale).deleteExisting() }
        log("Removed ${displayExistingPath(absolutePathOf(outputRoot.toString()), stale)}, which this definition no longer produces.")
    }
    // Before the writing: on a case insensitive file system an emptied `Api/` still standing would
    // take the pages written to `API/` and keep its old spelling.
    removeEmptyDirectories(outputRoot)
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
}

internal fun compareDocuments(outputRoot: Path, pages: Map<String, String>): DocumentDifference {
    val existing = existingPagesOf(outputRoot)
    // By the names the directory lists: a case insensitive file system also opens `API.md` when
    // only `Api.md` is there.
    val listed = existing.toSet()
    val missing = mutableListOf<String>()
    val different = mutableListOf<String>()
    for ((path, content) in pages) {
        when {
            path !in listed -> missing += path
            catchingIo(path, outputRoot) { outputRoot.pageAt(path).readText() } != content -> different += path
        }
    }
    return DocumentDifference(
        missing = missing,
        different = different,
        extra = stalePagesOf(outputRoot, existing, pages.keys),
    )
}

internal class DocumentDifference(
    val missing: List<String>,
    val different: List<String>,
    val extra: List<String>,
) {
    val isUpToDate: Boolean get() = missing.isEmpty() && different.isEmpty() && extra.isEmpty()
}

// Compared by the spelling the directory lists. A case insensitive file system opens a page left
// under the old spelling through the new one too, and that page still has to go. The only one kept
// is a page whose generated spelling is listed as well and is the same file (a hard link, or a
// directory linked under the other spelling): that is the generated page itself.
private fun stalePagesOf(outputRoot: Path, existing: List<String>, generated: Set<String>): List<String> {
    val listed = existing.toSet()
    val generatedByLowercase = generated.associateBy { it.lowercase() }
    return existing.filterNot { page ->
        if (page in generated) return@filterNot true
        val counterpart = generatedByLowercase[page.lowercase()] ?: return@filterNot false
        counterpart in listed && isSameFile(outputRoot, page, counterpart)
    }
}

private fun isSameFile(outputRoot: Path, existing: String, generated: String): Boolean =
    catchingIo(existing, outputRoot) {
        Files.isSameFile(outputRoot.pageAt(existing), outputRoot.pageAt(generated))
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
