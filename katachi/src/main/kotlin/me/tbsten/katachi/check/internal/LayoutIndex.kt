package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.NearbyLocation
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.CaptureMismatchDetail
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.internal.captureMismatchOf
import java.util.IdentityHashMap

/**
 * The flattened layout of every role, turned into the four questions the traversal asks.
 *
 * Whether a file is allowed is decided by the union of every role's claims, and a file two
 * roles both allow is not a problem (the generated documentation lists both). The
 * roles are kept alongside the patterns rather than dropped, because the answer the traversal
 * needs is not only "is this allowed" but "by whom": a processor asking `filesOf(role)` is
 * asking that same question from the other side.
 */
internal class LayoutIndex(entries: List<LayoutEntry>) {
    /**
     * Patterns matching a directory the traversal may descend into: every directory that was
     * declared, and every directory on the way down to something that was.
     */
    private val knownDirectories: PrefixGlobIndex<Glob> = PrefixGlobIndex(
        entries.flatMapTo(linkedSetOf<String>()) { it.directoryPatterns() }.map { Glob.compile(it) },
    ) { it }

    private val allowedFiles: PrefixGlobIndex<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.File }.indexedByGlob()

    /** Directories whose direct children are all allowed, from `anyFile()`. */
    private val openDirectories: PrefixGlobIndex<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.AnyFile }.indexedByGlob()

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
    private val claimedFiles: PrefixGlobIndex<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.File && !it.synthetic }.indexedByGlob()

    private val claimedOpenDirectories: PrefixGlobIndex<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.AnyFile && !it.synthetic }.indexedByGlob()

    /** Directories nothing below is looked at in, from `ignore()`. */
    private val ignoredDirectories: PrefixGlobIndex<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.Ignore }.indexedByGlob()

    /** Declarations that have to be matched by a real file, in declaration order. */
    val requiredFiles: List<LayoutEntry> = entries.filter { it.required }

    private val nearby: NearbyCandidates = NearbyCandidates(entries)

    /** Whether the check stops at [directory] because a role said to. */
    fun isIgnored(directory: String): Boolean = ignoredDirectories.anyMatches(directory)

    /** Whether any role declared [directory] itself, or something below it. */
    fun isKnown(directory: String): Boolean = knownDirectories.anyMatches(directory)

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
     * them is an [me.tbsten.katachi.check.AmbiguousLayout] a walk found rather than a reading of the declarations.
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

    /**
     * The entries whose pattern matches [file] but whose bound captures hold it back, with why,
     * in declaration order -- what a file [rolesOf] answers nobody for is reported by, when
     * there is one. See [me.tbsten.katachi.dsl.internal.captureMismatchOf].
     */
    fun captureMismatchesOn(file: String): List<Pair<LayoutEntry, CaptureMismatchDetail>> {
        val mismatches = mutableListOf<Pair<LayoutEntry, CaptureMismatchDetail>>()
        for (entry in allowedFiles.matching(file)) {
            entry.captureMismatchOf(file)?.let { mismatches += entry to it }
        }
        val directory = file.parentPath()
        if (directory.isNotEmpty()) {
            for (entry in openDirectories.matching(directory)) {
                entry.captureMismatchOf(file)?.let { mismatches += entry to it }
            }
        }
        // The two lists are each in declaration order; interleaving them back is what gives the
        // report the first declared one.
        return mismatches.sortedBy { (entry, _) -> declarationOrder.getValue(entry) }
    }

    /** Where each entry was declared, by identity: `LayoutEntry` declares no `equals`. */
    private val declarationOrder: Map<LayoutEntry, Int> =
        IdentityHashMap<LayoutEntry, Int>().also { order -> entries.forEachIndexed { index, entry -> order[entry] = index } }

    /**
     * One entry per role out of [fileEntries] and [openEntries], first match winning. An entry
     * whose pattern matches but whose bound captures hold the file back does not count.
     */
    private fun matchesOn(
        file: String,
        fileEntries: PrefixGlobIndex<LayoutEntry>,
        openEntries: PrefixGlobIndex<LayoutEntry>,
    ): Map<Role, LayoutEntry> {
        val matched = LinkedHashMap<Role, LayoutEntry>()
        for (entry in fileEntries.matching(file)) {
            if (entry.captureMismatchOf(file) == null) matched.putIfAbsent(entry.role, entry)
        }
        val directory = file.parentPath()
        // `anyFile()` covers the files directly inside a directory and nothing deeper, and
        // the root itself is not a directory any role can declare.
        if (directory.isNotEmpty()) {
            for (entry in openEntries.matching(directory)) {
                if (entry.captureMismatchOf(file) == null) matched.putIfAbsent(entry.role, entry)
            }
        }
        return matched
    }

    /**
     * Places [file] could be moved to, nearest first, three at most. The rule is
     * [NearbyCandidates]'s; a location sharing nothing with the file is left out rather than
     * padding the list, because a suggestion that far away is noise.
     */
    fun nearbyOf(file: String): List<NearbyLocation> = nearby.nearbyOf(file)
}

private fun List<LayoutEntry>.indexedByGlob(): PrefixGlobIndex<LayoutEntry> = PrefixGlobIndex(this) { it.glob }

/** `core/domain` for `core/domain/UseCase.kt`, and the empty string at the root. */
internal fun String.parentPath(): String = substringBeforeLast('/', missingDelimiterValue = "")

private fun LayoutEntry.directoryPatterns(): List<String> {
    val segments = path.split('/')
    // Every level above the last one is a directory whatever this entry is; the last level
    // is one only when the entry is not a file.
    val levels = if (kind == LayoutEntryKind.File) segments.size - 1 else segments.size
    return (1..levels).map { segments.take(it).joinToString("/") }
}
