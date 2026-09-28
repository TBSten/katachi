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
internal fun newMenuTreeOf(availability: EntryAvailability, matches: List<PlacementMatch>): List<NewMenuNode> = when (availability) {
    EntryAvailability.NotLoaded, EntryAvailability.Loading -> listOf(NewMenuNode.Loading)
    EntryAvailability.Unavailable -> emptyList()
    EntryAvailability.Ready -> readyTree(matches.distinctBy { it.id })
}

private fun readyTree(matches: List<PlacementMatch>): List<NewMenuNode> {
    val definitions = matches.map { it.definition }.distinct()
    // The definition level is only worth a click when there is a choice (issue 7).
    if (definitions.size < 2) return nodesAt(matches.map(::Entry), depth = 0)
    return definitions.map { definition ->
        NewMenuNode.Definition(definition, nodesAt(matches.filter { it.definition == definition }.map(::Entry), depth = 0))
    }
}

/** A match with its role name split into the groups and the role. */
private class Entry(val match: PlacementMatch) {
    val segments: List<String> = match.template.template.roleName.split('.').filter { it.isNotEmpty() }
}

/** The nodes of [entries] at [depth] of their role paths, in the order they first appear (declaration order). */
private fun nodesAt(entries: List<Entry>, depth: Int): List<NewMenuNode> {
    // (isGroup, name) -> entries, first appearance first; a group and a role may share a name.
    val byNode = LinkedHashMap<Pair<Boolean, String>, MutableList<Entry>>()
    for (entry in entries) {
        val isGroup = entry.segments.size - 1 > depth
        val name = if (isGroup) entry.segments[depth] else entry.segments.lastOrNull().orEmpty()
        byNode.getOrPut(isGroup to name) { mutableListOf() } += entry
    }
    return byNode.map { (key, members) ->
        val (isGroup, name) = key
        when {
            isGroup -> NewMenuNode.Group(name, nodesAt(members, depth + 1))
            // A role with one template is itself the item; its remaining path follows the name (issue 1).
            members.size == 1 -> NewMenuNode.Template(members.single().match, labelOf(name, members.single().match))
            else -> NewMenuNode.Role(name, members.map { NewMenuNode.Template(it.match, labelOf(it.match.template.template.title, it.match)) })
        }
    }
}

private fun labelOf(name: String, match: PlacementMatch): String =
    if (match.remainingPath.isBlank()) name else "$name \u2014 ${match.remainingPath}"
