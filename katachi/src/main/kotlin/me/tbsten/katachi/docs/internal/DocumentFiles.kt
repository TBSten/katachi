package me.tbsten.katachi.docs.internal

import java.io.File
import java.io.IOException
import me.tbsten.katachi.docs.KatachiDocumentIoException

/**
 * Makes [outputRoot] hold exactly [pages], and says through [log] everything it did.
 *
 * The output directory is katachi's. A page this run did not produce -- the page of a role that
 * was renamed, or of a group that was deleted -- is removed rather than left behind, because a
 * stale page is still linked to by nothing and still found by a reader browsing the tree. That
 * is the same failure `KatachiDocumentPathCollisionException` refuses to allow at generation
 * time, arriving one run later.
 *
 * Only `*.md` is ever deleted, which bounds what "katachi's" means. A user who points
 * `outputDir` at a directory holding images, a handwritten `.mdx`, or a `.gitkeep` keeps all of
 * them, and every removal is logged, so nothing leaves without being named.
 *
 * Deleting comes before writing, and the stale set is decided ignoring case. On a case
 * insensitive filesystem `UseCase.md` and `Usecase.md` are one file, so a role renamed only in
 * case would otherwise have its freshly written page deleted as the old one.
 */
internal fun writeDocuments(outputRoot: File, pages: Map<String, String>, log: (String) -> Unit) {
    val generated = pages.keys.mapTo(mutableSetOf()) { it.lowercase() }
    for (stale in existingPagesOf(outputRoot).filterNot { it.lowercase() in generated }) {
        val file = File(outputRoot, stale)
        catchingIo(stale, outputRoot) { if (!file.delete()) throw IOException("delete returned false") }
        log("Removed $stale, which this definition no longer produces.")
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

/**
 * What [outputRoot] would have to change for it to hold exactly [pages], without changing it.
 *
 * The other half of [writeDocuments] and nothing more: the two agree about what counts as a
 * page of katachi's, so what this reports is exactly what a write would do.
 */
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

/** How [compareDocuments] found the output directory, relative to what the definition produces. */
internal class DocumentDifference(
    /** Pages the definition produces that are not on disk at all. */
    val missing: List<String>,
    /** Pages that are on disk with other contents. */
    val different: List<String>,
    /** Pages on disk that this run does not produce, which a write would have removed. */
    val extra: List<String>,
) {
    /** Whether the output directory already holds exactly what the definition produces. */
    val isUpToDate: Boolean get() = missing.isEmpty() && different.isEmpty() && extra.isEmpty()
}

/**
 * Every page katachi could have written that is below [outputRoot] now, sorted.
 *
 * Sorted rather than left in the filesystem's order so that a message reads the same on every
 * machine. A directory that is not there yet is no pages rather than an error -- the first run
 * of a generator is the ordinary case.
 */
private fun existingPagesOf(outputRoot: File): List<String> {
    if (!outputRoot.isDirectory) return emptyList()
    return outputRoot.walkTopDown()
        .filter { it.isFile && it.name.endsWith(PAGE_EXTENSION) }
        .map { it.toRelativeString(outputRoot).replace(File.separatorChar, '/') }
        .sorted()
        .toList()
}

/**
 * Drops the directories a removal emptied, bottom up so that a parent left empty by its last
 * child goes too.
 *
 * [outputRoot] itself stays: a user pointed `outputDir` at it, and a generator that answers an
 * empty definition by deleting the directory it was given is answering more than it was asked.
 */
private fun removeEmptyDirectories(outputRoot: File) {
    if (!outputRoot.isDirectory) return
    outputRoot.walkBottomUp()
        .filter { it != outputRoot && it.isDirectory }
        .forEach { it.delete() }
}

/**
 * Runs [block], turning whatever the filesystem refuses into an exception that says which page
 * and which output directory it was about.
 *
 * A bare `FileNotFoundException` names the path and nothing else, so a reader cannot tell a
 * mistyped `--arg outputDir=` from a directory they have no permission to write.
 */
private fun <T> catchingIo(path: String, outputRoot: File, block: () -> T): T = try {
    block()
} catch (cause: IOException) {
    throw KatachiDocumentIoException(path = path, outputDir = outputRoot.path, cause = cause)
} catch (cause: SecurityException) {
    throw KatachiDocumentIoException(path = path, outputDir = outputRoot.path, cause = cause)
}
