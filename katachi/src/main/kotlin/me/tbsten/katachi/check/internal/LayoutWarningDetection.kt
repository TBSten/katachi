package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.AmbiguousLayout
import me.tbsten.katachi.check.LayoutClaim
import me.tbsten.katachi.check.MissingDescription
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role

internal fun layoutWarningsOf(entries: List<LayoutEntry>, fileOverlaps: List<FileOverlap>): List<Violation> {
    val declared = ambiguousLayoutsOf(entries)
    return declared + ambiguousFilesOf(fileOverlaps, declared) + missingDescriptionsOf(entries)
}

internal fun missingDescriptionsOf(entries: List<LayoutEntry>): List<Violation> {
    val byRole = LinkedHashMap<Role, MutableList<LayoutEntry>>()
    for (entry in entries) byRole.getOrPut(entry.role) { mutableListOf() } += entry

    return byRole.values.flatMap { roleEntries ->
        val places = roleEntries.filter { it.place && holdsAClaim(it.path, roleEntries) }
        if (places.size < 2) return@flatMap emptyList()
        places.filter { it.description == null }.map { place ->
            MissingDescription(
                path = place.path,
                role = place.role,
                // Identity, not `it.path != place.path`: two places of one role never share a
                // path, and comparing the objects says exactly what is meant here.
                otherPlaces = places.filterNot { it === place }.map { it.path },
                declaredAt = place.declaredAt,
            )
        }
    }
}

// Directory is the only kind that is not a claim; written as an exclusion so a new kind counts by default.
private fun holdsAClaim(path: String, roleEntries: List<LayoutEntry>): Boolean =
    roleEntries.any { entry ->
        !entry.synthetic &&
            entry.kind != LayoutEntryKind.Directory &&
            (entry.path == path || entry.path.startsWith("$path/"))
    }

// Skips Directory, Ignore and synthetic (module-injected) entries: none of them claims a file, so sharing them is not ambiguity.
internal fun ambiguousLayoutsOf(entries: List<LayoutEntry>): List<AmbiguousLayout> {
    val claimable = entries
        .asSequence()
        .filterNot { it.synthetic }
        .filter { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }

    val byPath = LinkedHashMap<String, LinkedHashMap<Role, LayoutEntry>>()
    for (entry in claimable) {
        byPath.getOrPut(entry.path) { LinkedHashMap() }.putIfAbsent(entry.role, entry)
    }

    return byPath.mapNotNull { (path, byRole) ->
        if (byRole.size < 2) return@mapNotNull null
        AmbiguousLayout(
            path = path,
            claims = byRole.values.map { LayoutClaim(role = it.role, declaredAt = it.declaredAt) },
            // Nothing was walked to find this, and that is what the empty list states.
            overlappingFiles = emptyList(),
        )
    }
}
