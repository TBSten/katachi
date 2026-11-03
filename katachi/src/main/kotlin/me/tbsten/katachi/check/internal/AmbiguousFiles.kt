package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.AmbiguousLayout
import me.tbsten.katachi.check.LayoutClaim
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role

internal class FileOverlap(
    val roles: Set<Role>,
    // Always two or more; read off the first file of the group.
    val claims: List<LayoutClaim>,
) {
    val files: MutableList<String> = mutableListOf()
}

internal class FileOverlaps {
    private val byRoles = LinkedHashMap<Set<Role>, FileOverlap>()

    fun record(path: String, claims: List<LayoutEntry>) {
        if (claims.size < 2) return
        // `LayoutIndex.claimsOn` already keeps one entry per role, so this set is exactly as
        // large as `claims` and the two stay aligned.
        val roles = claims.mapTo(LinkedHashSet()) { it.role }
        byRoles.getOrPut(roles) {
            FileOverlap(
                roles = roles,
                claims = claims.map { LayoutClaim(role = it.role, declaredAt = it.declaredAt) },
            )
        }.files += path
    }

    fun toList(): List<FileOverlap> = byRoles.values.toList()
}

internal fun ambiguousFilesOf(
    overlaps: List<FileOverlap>,
    declared: List<AmbiguousLayout>,
): List<AmbiguousLayout> {
    val alreadyReported = declared.mapTo(HashSet()) { warning ->
        warning.claims.mapTo(HashSet()) { it.role }
    }
    return overlaps
        .filterNot { it.roles in alreadyReported }
        .map { overlap ->
            AmbiguousLayout(
                // The first file the walk reached, so the path a reader is sent to is one that
                // exists — unlike the declaration-only half, where it is the pattern text.
                path = overlap.files.first(),
                claims = overlap.claims,
                overlappingFiles = overlap.files.toList(),
            )
        }
}
