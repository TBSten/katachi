package me.tbsten.katachi.intellij.presentation.entry

import me.tbsten.katachi.intellij.model.KatachiModule

/**
 * A node under New › katachi (issue 12): [definition ›] group › role › template. A role with one
 * template is itself the item. Every leaf is one template; the tree never repeats one.
 */
internal sealed interface NewMenuNode {
    /** The top level, only when the matches come from two or more definitions (issue 7). */
    data class Definition(val definition: KatachiModule, val children: List<NewMenuNode>) : NewMenuNode

    data class Group(val name: String, val children: List<NewMenuNode>) : NewMenuNode

    data class Role(val name: String, val children: List<NewMenuNode>) : NewMenuNode

    /** Picking it opens the dialog on [match]'s template. [label] shows the remaining path (issue 1). */
    data class Template(val match: PlacementMatch, val label: String) : NewMenuNode

    /** "katachi (loading…)": the one disabled item before the list is loaded (issue 18). */
    data object Loading : NewMenuNode
}

/**
 * The children of New › katachi for [matches] (index order) under [availability]. Empty means the
 * katachi group is hidden.
 *
 * ```kotlin
 * newMenuTreeOf(EntryAvailability.NotLoaded, emptyList()) // [NewMenuNode.Loading]
 * ```
 */
internal fun newMenuTreeOf(availability: EntryAvailability, matches: List<PlacementMatch>): List<NewMenuNode> {
    // TODO(A3): the tree, the loading item, and nothing when unavailable.
    return emptyList()
}
