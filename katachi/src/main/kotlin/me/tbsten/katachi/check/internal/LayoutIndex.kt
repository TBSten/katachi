package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.NearbyLocation
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.CaptureMismatchDetail
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.internal.captureMismatchOf
import java.util.IdentityHashMap

internal class LayoutIndex(entries: List<LayoutEntry>) {
    private val knownDirectories: PrefixGlobIndex<Glob> = PrefixGlobIndex(
        entries.flatMapTo(linkedSetOf<String>()) { it.directoryPatterns() }.map { Glob.compile(it) },
    ) { it }

    private val allowedFiles: PrefixGlobIndex<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.File }.indexedByGlob()

    private val openDirectories: PrefixGlobIndex<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.AnyFile }.indexedByGlob()

    // Drops what a `module { }` key injects: every role sharing a module gets its own build.gradle.kts from the sugar, which must not count as a claim.
    private val claimedFiles: PrefixGlobIndex<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.File && !it.synthetic }.indexedByGlob()

    private val claimedOpenDirectories: PrefixGlobIndex<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.AnyFile && !it.synthetic }.indexedByGlob()

    private val ignoredDirectories: PrefixGlobIndex<LayoutEntry> =
        entries.filter { it.kind == LayoutEntryKind.Ignore }.indexedByGlob()

    val requiredFiles: List<LayoutEntry> = entries.filter { it.required }

    private val nearby: NearbyCandidates = NearbyCandidates(entries)

    fun isIgnored(directory: String): Boolean = ignoredDirectories.anyMatches(directory)

    fun isKnown(directory: String): Boolean = knownDirectories.anyMatches(directory)

    fun rolesOf(file: String): List<Role> = matchesOn(file, allowedFiles, openDirectories).keys.toList()

    fun claimsOn(file: String): List<LayoutEntry> =
        matchesOn(file, claimedFiles, claimedOpenDirectories).values.toList()

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

    private val declarationOrder: Map<LayoutEntry, Int> =
        IdentityHashMap<LayoutEntry, Int>().also { order -> entries.forEachIndexed { index, entry -> order[entry] = index } }

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

    fun nearbyOf(file: String): List<NearbyLocation> = nearby.nearbyOf(file)
}

private fun List<LayoutEntry>.indexedByGlob(): PrefixGlobIndex<LayoutEntry> = PrefixGlobIndex(this) { it.glob }

internal fun String.parentPath(): String = substringBeforeLast('/', missingDelimiterValue = "")

private fun LayoutEntry.directoryPatterns(): List<String> {
    val segments = path.split('/')
    // Every level above the last one is a directory whatever this entry is; the last level
    // is one only when the entry is not a file.
    val levels = if (kind == LayoutEntryKind.File) segments.size - 1 else segments.size
    return (1..levels).map { segments.take(it).joinToString("/") }
}
