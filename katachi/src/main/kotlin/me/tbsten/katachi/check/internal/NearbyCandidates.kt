package me.tbsten.katachi.check.internal

import kotlin.math.abs
import me.tbsten.katachi.check.NearbyLocation
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.DeclaredByGradle
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.internal.GlobLexer

private const val NEARBY_LIMIT: Int = 3

internal class NearbyCandidates(entries: List<LayoutEntry>) {
    // Built on the first unexpected file, not up front: a clean walk never asks for a neighbour,
    // and building the list compiles a glob per level of every entry.
    private val candidates: List<NearbyCandidate> by lazy { entries.candidates() }

    fun nearbyOf(file: String): List<NearbyLocation> {
        val directory = file.parentPath()
        // The root is not a directory any role can declare, so a file there has no neighbour.
        if (directory.isEmpty()) return emptyList()
        val levels = directory.split('/')
        val name = file.substringAfterLast('/')
        val best = ArrayList<Ranked>(NEARBY_LIMIT + 1)
        for (candidate in candidates) {
            if (!acceptsExtension(candidate.filePattern, name)) continue
            val filled = instantiate(candidate.segments, levels) ?: continue
            if (filled.firstOrNull() != levels[0]) continue
            // Only the three nearest are wanted, and the level count alone bounds the
            // distance from below, so most of a large layout is dropped before any table.
            val third = best.getOrNull(NEARBY_LIMIT - 1)
            if (third != null && abs(filled.size - levels.size) > third.segments) continue
            val ranked = Ranked(candidate, filled, levels, name)
            val same = best.indexOfFirst {
                it.candidate.role.qualifiedName == candidate.role.qualifiedName && it.directory == filled
            }
            if (same >= 0) {
                // One line per role and directory: a role that declared `X.kt` and `*.kt`
                // in the same directory is offered once, with whichever came out nearer.
                if (RANKING.compare(ranked, best[same]) >= 0) continue
                best.removeAt(same)
            } else if (third != null && RANKING.compare(ranked, third) >= 0) {
                continue
            }
            val position = best.indexOfFirst { RANKING.compare(ranked, it) < 0 }
            best.add(if (position < 0) best.size else position, ranked)
            if (best.size > NEARBY_LIMIT) best.removeAt(best.size - 1)
        }
        return best.map { NearbyLocation(it.candidate.role, it.directory.joinToString("/"), it.candidate.filePattern) }
    }
}

private class NearbyCandidate(
    val role: Role,
    val segments: List<NearbySegment>,
    val filePattern: String?,
    val order: Int,
) {
    private val fileGlob: Glob? = filePattern?.let { Glob.compile(it) }

    fun matchesName(name: String): Boolean = fileGlob?.matches(name) ?: true
}

private class Ranked(
    val candidate: NearbyCandidate,
    val directory: List<String>,
    fileDirectory: List<String>,
    fileName: String,
) {
    val segments: Int = segmentDistance(fileDirectory, directory)
    val nameMismatch: Int = if (candidate.matchesName(fileName)) 0 else 1
    val characters: Int by lazy { characterDistance(fileDirectory, directory) }
}

private val RANKING: Comparator<Ranked> =
    compareBy({ it.segments }, { it.nameMismatch }, { it.characters }, { it.candidate.order })

private fun List<LayoutEntry>.candidates(): List<NearbyCandidate> {
    val seen = LinkedHashMap<Triple<String, String, String?>, NearbyCandidate>()
    for (entry in this) {
        if (entry.synthetic || entry.role[DeclaredByGradle] == true) continue
        val directory: String
        val filePattern: String?
        when (entry.kind) {
            LayoutEntryKind.File -> {
                directory = entry.path.parentPath()
                filePattern = entry.path.substringAfterLast('/')
            }

            LayoutEntryKind.AnyFile -> {
                directory = entry.path
                filePattern = null
            }

            LayoutEntryKind.Directory, LayoutEntryKind.Ignore -> continue
        }
        if (directory.isEmpty()) continue
        // Each way a `{a,b}` can be written out is a place of its own, in declaration order.
        // A declaration with too many has no useful single answer, so it offers none.
        val directories = GlobLexer.expandBraces(directory) ?: continue
        for (written in directories) {
            val segments = nearbySegmentsOf(written)
            // Two `**` in one pattern have no single way of being filled in from one path.
            if (segments.count { it == NearbySegment.DoubleStar } > 1) continue
            seen.getOrPut(Triple(entry.role.qualifiedName, written, filePattern)) {
                NearbyCandidate(entry.role, segments, filePattern, order = seen.size)
            }
        }
    }
    return seen.values.toList()
}
