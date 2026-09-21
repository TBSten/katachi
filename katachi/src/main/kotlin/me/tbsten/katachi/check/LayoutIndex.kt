package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind

/** How many locations an `[UnexpectedFile]` block offers. */
private const val NEARBY_LIMIT: Int = 3

/**
 * The flattened layout of every role, turned into the four questions the traversal asks.
 *
 * The roles are gone by this point on purpose — except where a violation has to name one.
 * Whether a file is allowed is decided by the union of every role's claims, and a file two
 * roles both allow is not a problem (from v0.3 the generated documentation lists both).
 */
@OptIn(InternalKatachiApi::class)
internal class LayoutIndex(entries: List<LayoutEntry>) {
    /**
     * Patterns matching a directory the traversal may descend into: every directory that was
     * declared, and every directory on the way down to something that was.
     */
    private val knownDirectories: List<Glob> =
        entries.flatMapTo(linkedSetOf<String>()) { it.directoryPatterns() }.map { Glob.compile(it) }

    private val allowedFiles: List<Glob> =
        entries.filter { it.kind == LayoutEntryKind.File }.map { it.glob }

    /** Directories whose direct children are all allowed, from `anyFile()`. */
    private val openDirectories: List<Glob> =
        entries.filter { it.kind == LayoutEntryKind.AnyFile }.map { it.glob }

    /** Directories nothing below is looked at in, from `ignore()`. */
    private val ignoredDirectories: List<Glob> =
        entries.filter { it.kind == LayoutEntryKind.Ignore }.map { it.glob }

    /** Declarations that have to be matched by a real file, in declaration order. */
    val requiredFiles: List<LayoutEntry> = entries.filter { it.required }

    private val locations: List<NearbyLocation> = entries.nearbyLocations()

    /** Whether the check stops at [directory] because a role said to. */
    fun isIgnored(directory: String): Boolean = ignoredDirectories.any { it.matches(directory) }

    /** Whether any role declared [directory] itself, or something below it. */
    fun isKnown(directory: String): Boolean = knownDirectories.any { it.matches(directory) }

    /** Whether any role allows a file to sit at [file]. */
    fun allowsFile(file: String): Boolean {
        if (allowedFiles.any { it.matches(file) }) return true
        val directory = file.parentPath()
        // `anyFile()` covers the files directly inside a directory and nothing deeper, and
        // the root itself is not a directory any role can declare.
        return directory.isNotEmpty() && openDirectories.any { it.matches(directory) }
    }

    /**
     * Places [file] could be moved to, the ones sharing the most leading path with it first.
     *
     * A location sharing nothing with the file is left out rather than padding the list: a
     * suggestion that far away is noise, and the block still ends with a role to paste.
     * Locations that tie keep declaration order, so the roles line up in the report the same
     * way they do in the definition.
     */
    fun nearbyOf(file: String): List<NearbyLocation> {
        val directory = file.parentPath()
        return locations
            .map { it to sharedLeadingSegments(directory, it.directory) }
            .filter { (_, shared) -> shared > 0 }
            .sortedByDescending { (_, shared) -> shared }
            .take(NEARBY_LIMIT)
            .map { (location, _) -> location }
    }
}

/** `core/domain` for `core/domain/UseCase.kt`, and the empty string at the root. */
internal fun String.parentPath(): String = substringBeforeLast('/', missingDelimiterValue = "")

@OptIn(InternalKatachiApi::class)
private fun LayoutEntry.directoryPatterns(): List<String> {
    val segments = path.split('/')
    // Every level above the last one is a directory whatever this entry is; the last level
    // is one only when the entry is not a file.
    val levels = if (kind == LayoutEntryKind.File) segments.size - 1 else segments.size
    return (1..levels).map { segments.take(it).joinToString("/") }
}

/**
 * The directories a role actually accepts a file into: where it declared one, and where it
 * wrote `anyFile()`.
 *
 * A directory that only exists to hold another directory is not one of them. `core/domain`,
 * passed through on the way to the `useCase` directory below it, accepts nothing itself, so
 * offering it as somewhere to move a file to would be wrong as well as unhelpful. A
 * directory the role stopped checking in is left out for the same reason: moving a file
 * there hides it instead of giving it a role.
 */
@OptIn(InternalKatachiApi::class)
private fun List<LayoutEntry>.nearbyLocations(): List<NearbyLocation> {
    val seen = LinkedHashMap<Pair<String, String>, NearbyLocation>()
    for (entry in this) {
        val directory = when (entry.kind) {
            LayoutEntryKind.File -> entry.path.parentPath()
            LayoutEntryKind.AnyFile -> entry.path
            LayoutEntryKind.Directory, LayoutEntryKind.Ignore -> continue
        }
        // A pattern is not somewhere a file can be moved to, and the project root is not a
        // suggestion worth printing.
        if (directory.isEmpty() || '*' in directory) continue
        seen.getOrPut(entry.role.qualifiedName to directory) {
            NearbyLocation(role = entry.role, directory = directory)
        }
    }
    return seen.values.toList()
}

private fun sharedLeadingSegments(left: String, right: String): Int {
    if (left.isEmpty() || right.isEmpty()) return 0
    val leftSegments = left.split('/')
    val rightSegments = right.split('/')
    var shared = 0
    while (shared < leftSegments.size && shared < rightSegments.size) {
        if (leftSegments[shared] != rightSegments[shared]) break
        shared++
    }
    return shared
}
