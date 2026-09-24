package me.tbsten.katachi.scan

import me.tbsten.katachi.dsl.Glob
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role

/** How many locations an `[UnexpectedFile]` block offers. */
private const val NEARBY_LIMIT: Int = 3

/**
 * The flattened layout of every role, turned into the four questions the traversal asks.
 *
 * Whether a file is allowed is decided by the union of every role's claims, and a file two
 * roles both allow is not a problem (from v0.3 the generated documentation lists both). The
 * roles are kept alongside the patterns rather than dropped, because the answer the traversal
 * needs is not only "is this allowed" but "by whom": a processor asking `filesOf(role)` is
 * asking that same question from the other side.
 */
internal class LayoutIndex(entries: List<LayoutEntry>) {
    /**
     * Patterns matching a directory the traversal may descend into: every directory that was
     * declared, and every directory on the way down to something that was.
     */
    private val knownDirectories: List<Glob> =
        entries.flatMapTo(linkedSetOf<String>()) { it.directoryPatterns() }.map { Glob.compile(it) }

    private val allowedFiles: List<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.File }

    /** Directories whose direct children are all allowed, from `anyFile()`. */
    private val openDirectories: List<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.AnyFile }

    /**
     * The same two lists with what a `module { }` key injects dropped — what [claimsOn]
     * answers from, and the one place the two questions come apart.
     *
     * Every role sharing a module gets its own `build.gradle.kts` entry from the sugar, so
     * that file is matched by all of them; [rolesOf] has to keep those entries, or the file
     * would be an `[UnexpectedFile]` in a project that declares the module perfectly well.
     * Nobody *claimed* it, though: the roles' authors wrote `.module { }`, not that line. So
     * the ambiguity question reads these, exactly as `ambiguousLayoutsOf` drops the same
     * entries before grouping by path text.
     */
    private val claimedFiles: List<LayoutEntry> = allowedFiles.filterNot { it.synthetic }

    private val claimedOpenDirectories: List<LayoutEntry> = openDirectories.filterNot { it.synthetic }

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

    /**
     * Every role that allows a file to sit at [file], in declaration order, or empty when the
     * file is allowed by nobody — which is exactly when it is an `[UnexpectedFile]`.
     *
     * All of them, not the first one: the check lets two roles claim overlapping patterns, so
     * stopping at the first match would make `filesOf(role)` disagree with the very rule that
     * allowed the file. A role claiming the same path twice still shows up once, which is what
     * keying the matches by role is for.
     */
    fun rolesOf(file: String): List<Role> = matchesOn(file, allowedFiles, openDirectories).keys.toList()

    /**
     * The declarations that claim [file] as their role's own, one per role — two or more of
     * them is an [AmbiguousLayout] a walk found rather than a reading of the declarations.
     *
     * The entry rather than the role, because a report has to point back at the line that
     * wrote it, and with different patterns matching one file there is no declaration site to
     * be derived from the role alone.
     *
     * The roles that named the file come before the roles that only left its directory open,
     * and declaration order holds within each of those two groups. Its result is always a
     * subset of [rolesOf]'s, which is what lets the walk ask this only for a file [rolesOf]
     * already answered with two roles or more.
     */
    fun claimsOn(file: String): List<LayoutEntry> =
        matchesOn(file, claimedFiles, claimedOpenDirectories).values.toList()

    /** One entry per role out of [fileEntries] and [openEntries], first match winning. */
    private fun matchesOn(
        file: String,
        fileEntries: List<LayoutEntry>,
        openEntries: List<LayoutEntry>,
    ): Map<Role, LayoutEntry> {
        val matched = LinkedHashMap<Role, LayoutEntry>()
        for (entry in fileEntries) {
            if (entry.glob.matches(file)) matched.putIfAbsent(entry.role, entry)
        }
        val directory = file.parentPath()
        // `anyFile()` covers the files directly inside a directory and nothing deeper, and
        // the root itself is not a directory any role can declare.
        if (directory.isNotEmpty()) {
            for (entry in openEntries) {
                if (entry.glob.matches(directory)) matched.putIfAbsent(entry.role, entry)
            }
        }
        return matched
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
