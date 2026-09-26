package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.AmbiguousLayout
import me.tbsten.katachi.check.LayoutClaim
import me.tbsten.katachi.check.MissingDescription
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role

/**
 * Katachi's Warning violations: what the declarations say by themselves, plus the overlaps
 * only the walk can see.
 *
 * [entries] is the unresolved, un-walked flattening. Two of the three detectors need nothing
 * else — whether two roles wrote one pattern, and whether a place with more than one role is
 * missing its `description`, are questions the declarations answer on their own. The third
 * cannot be answered that way at all, so [fileOverlaps] arrives already collected by the walk;
 * see [FileOverlaps].
 *
 * The order is the order a report prints: the textual overlaps, then the ones found on files,
 * then the unexplained places. See `me.tbsten.katachi.check.LayoutCheck`, which adds this to
 * the errors the same walk found.
 */
internal fun layoutWarningsOf(entries: List<LayoutEntry>, fileOverlaps: List<FileOverlap>): List<Violation> {
    val declared = ambiguousLayoutsOf(entries)
    return declared + ambiguousFilesOf(fileOverlaps, declared) + missingDescriptionsOf(entries)
}

/**
 * The places of a role that has more than one and left this one without a `description`.
 *
 * ### Which declarations count as a place
 *
 * A place is an entry carrying [LayoutEntry.place] — the directory a `module { }` key opened —
 * that also **holds a claim**: somewhere at or below it, the same role declared a
 * [LayoutEntryKind.File], [LayoutEntryKind.AnyFile] or [LayoutEntryKind.Ignore] of its own.
 *
 * The second half is what keeps `":katachi".module { }` written for its build script alone out of
 * the count. Such a block declares nothing but the two lines the sugar injects, which every Gradle
 * module has; asking its author to explain "which files belong here rather than in the others"
 * would be asking about files that do not exist. Only a place a role actually puts something in
 * is a place a reader can be sent to.
 *
 * ### What this deliberately does not see
 *
 * A role living in two plain directory blocks — `"core/domain" { }` and `"tool" { }` written
 * straight under `layout { }` — is not reported, however much it reads like two places. See
 * [me.tbsten.katachi.dsl.internal.LayoutNode.place] for why the mark stops at module keys: katachi's own
 * Gradle vocabulary is built out of plain directory blocks, and until a user's key can be told
 * from the vocabulary's own, a wider rule would report `src/main` and `src/main/kotlin` as two
 * homes of a role that has one.
 */
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

/**
 * Whether [roleEntries] puts anything of the role's own at [path] or below it.
 *
 * [LayoutEntryKind.Directory] is the one kind that does not count, and it is written as the
 * exclusion rather than the other three as the inclusion: a directory is a path on the way to
 * something, so whatever kinds this enum gains later, they are things declared *at* a path and
 * belong on the counting side.
 */
private fun holdsAClaim(path: String, roleEntries: List<LayoutEntry>): Boolean =
    roleEntries.any { entry ->
        !entry.synthetic &&
            entry.kind != LayoutEntryKind.Directory &&
            (entry.path == path || entry.path.startsWith("$path/"))
    }

/**
 * Paths two or more roles declare with the exact same pattern text.
 *
 * ### What is skipped, and why
 *
 * - **[LayoutEntryKind.Directory]** is a path on the way to a claim, not a claim itself. Six
 *   roles sharing one module all pass through `katachi`, `katachi/src`, `katachi/src/main`, and
 *   so on — that is `.module { }` doing exactly what it is for, not a mistake to flag.
 * - **[LayoutEntryKind.Ignore]** confers no ownership at all: `LayoutIndex.rolesOf` never
 *   reads `ignore()`, only [LayoutEntryKind.File] and [LayoutEntryKind.AnyFile] decide who a
 *   file belongs to. Two roles both writing `"secrets".ignore()` is two roles agreeing to look
 *   away from the same place, not two roles claiming a file placed there — "a file here
 *   belongs to all of them" would be a claim this violation never actually makes.
 * - **`synthetic` entries** — the `build` ignore and `build.gradle.kts` a `.module { }`
 *   injects — are dropped before grouping. Every role sharing a module injects the identical
 *   pair, so without this every pair of roles sharing one would report two warnings that mean
 *   nothing. A role that writes `"build.gradle.kts".file()` itself keeps its entry for that
 *   path (`LayoutEntry.mergedWith` turns `synthetic` off the moment a non-synthetic
 *   declaration of the same path exists for that role), so it can still collide with another
 *   role that did the same — the pair the report finds in that case is exactly the pair of
 *   roles that wrote it themselves, never the one that only let the sugar declare it.
 *
 * ### Grouping is by path text, and that is only half the check
 *
 * Grouping is by [LayoutEntry.path] alone — string equality, nothing smarter: a role writing
 * `"*.kt".file()` and another writing `"*ViewModel.kt".file()` in one directory are different
 * path text and never collide here, however completely the second's matches sit inside the
 * first's.
 *
 * That gap is not left open any more. [ambiguousFilesOf] closes it from the other side, out of
 * the walk: whatever the patterns say, two roles landing on one real file are reported. The two
 * halves are both kept because neither covers the other — a pattern matching no file has
 * nothing for a walk to find, and a walk needs no pattern to match to see an overlap — and a
 * pair of roles reported by this one is dropped from that one rather than printed twice.
 *
 * So what this half is now is the cheap, IO-free statement that stays true before a single file
 * exists, and reading it as "the whole of the overlap check" is the misreading to avoid.
 */
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
