package me.tbsten.katachi.scan

import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role

/**
 * One set of roles, and the real files the walk found all of them claiming.
 *
 * The declaration-only half of the ambiguity check compares pattern text and so cannot see
 * `"*.kt".file()` and `"*ViewModel.kt".file()` landing on the same file. This is what the walk
 * sees instead: not what two roles wrote, but what their patterns turned out to select.
 */
internal class FileOverlap(
    /**
     * The roles claiming every file in [files].
     *
     * A set rather than a list because it is the identity of the overlap — "these roles, on
     * top of each other" — and the order the walk happened to match them in is not part of
     * that. [claims] keeps the order a report prints.
     */
    val roles: Set<Role>,
    /**
     * Where each role declared its claim, in the order [LayoutIndex.claimsOn] matched them.
     * Always two or more.
     *
     * Read off the **first** file of the group. Two files of one group can be claimed through
     * different declarations of the same roles, and picking one keeps a report pointing at a
     * pair of lines a reader can go and edit; listing every declaration that took part would
     * turn the block back into the per-file flood this grouping exists to avoid.
     */
    val claims: List<LayoutClaim>,
) {
    /** The files, in walk order. Never empty by the time the walk hands this over. */
    val files: MutableList<String> = mutableListOf()
}

/**
 * The file-level overlaps of one walk, grouped by the roles that overlap **as the walk runs**.
 *
 * ### Why the group is the set of roles, and not the file
 *
 * A pair of roles overlapping at all is one decision its author has to make, however many
 * files it happens to land on. Reporting per file made the check unusable at the size it
 * matters: one real project's `ScreenComponent` (`*.kt`) and `ViewModel` (`*ViewModel.kt`)
 * overlapped on dozens of files across thirty-six modules, which is dozens of blocks saying
 * the identical sentence about the identical two lines of the definition. So the group is the
 * set of roles, one block names a representative file, and the rest are counted.
 *
 * Grouping while walking rather than afterwards is what keeps the cost flat: nothing holds a
 * role list per file, only one entry per pair of roles that actually collide.
 */
internal class FileOverlaps {
    private val byRoles = LinkedHashMap<Set<Role>, FileOverlap>()

    /**
     * Records [path] as claimed by [claims], and does nothing when fewer than two roles claim
     * it — which is the ordinary case and the reason this is safe to call per file.
     */
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

    /** The groups, first collision first. Empty when nothing overlapped. */
    fun toList(): List<FileOverlap> = byRoles.values.toList()
}

/**
 * [overlaps] as warnings, minus the ones [declared] already says.
 *
 * ### Why the same overlap is not reported twice
 *
 * Two roles that wrote the identical pattern are reported by `ambiguousLayoutsOf` from the
 * declarations alone, and the walk then finds every file that pattern matched — the same two
 * roles, the same two lines, and a block that would read the same. So a group whose roles are
 * already named by a declaration-only warning is dropped here rather than printed again.
 *
 * The match is on the set of roles, not on the path: the two halves disagree about what the
 * path *is* (a pattern there, a file here), and it is the pair of roles, not the string, that
 * the reader has to go and decide about. The cost is that a pair overlapping both textually
 * and, somewhere else, only semantically is reported once — one block, about the one decision
 * that fixes both.
 *
 * Keeping the declaration-only half is not redundancy: a pattern that matches no file at all
 * has nothing for a walk to find, and two roles claiming a path where a file is about to be
 * written are worth telling before it is.
 */
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
