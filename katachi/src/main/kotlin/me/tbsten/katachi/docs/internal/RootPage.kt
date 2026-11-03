package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.internal.MetadataValues

internal fun rootPage(
    title: String,
    description: String?,
    metadata: MetadataValues,
    roles: List<Role>,
    groups: List<Group>,
    placements: Map<Role, List<Placement>>,
): String = buildString {
    appendTitle(title + ROOT_TITLE_SUFFIX, metadata)
    appendDescriptionText(description)
    appendDocumentMap(roles, groups)
    appendRootRoles(roles)
    appendDirectoryTree(roles, placements, ROOT_PLACEMENT_HEADING)
    for (group in groups) appendGroupSection(group, depth = 0)
    appendDocumentSections(metadata)
    appendLine()
}

private fun StringBuilder.appendRootRoles(roles: List<Role>) {
    if (roles.isEmpty()) return
    append(SECTION_BREAK)
    append(ROOT_ROLES_HEADING)
    append(SECTION_BREAK)
    append(roles.joinToString("\n") { role -> "- ${roleLink(role)}${roleSummary(role)}" })
}

private fun StringBuilder.appendDocumentMap(roles: List<Role>, groups: List<Group>) {
    if (roles.isEmpty() && groups.isEmpty()) return
    append(SECTION_BREAK)
    append(DOCUMENT_MAP_HEADING)
    appendMapEntries(roles, groups, depth = 0)
}

private fun StringBuilder.appendMapEntries(roles: List<Role>, groups: List<Group>, depth: Int) {
    val nested = depth >= DEEPEST_MAP_HEADING_DEPTH
    val lines = mutableListOf<String>()
    for (role in roles) lines += "- ${roleLink(role)}"
    // Past the sixth level there is no heading left: `####### ` is not a heading in any renderer,
    // it is seven literal hashes. What is still nested says so by indenting instead.
    if (nested) for (group in groups) lines.addMapBullets(group, indent = 0)
    if (lines.isNotEmpty()) {
        append(SECTION_BREAK)
        append(lines.joinToString("\n"))
    }
    if (nested) return
    for (group in groups) {
        append(SECTION_BREAK)
        append("#".repeat(TOP_MAP_HEADING_HASHES + depth))
        append(" ")
        append(groupLink(group))
        // The group's own line of prose, under its heading in the index as well as in its
        // section below. A reader scanning the map to decide where to go is asking exactly what
        // a `summary` answers, and making them read the heading alone is making them guess.
        group[Summary]?.takeIf { it.isNotBlank() }?.let { summary ->
            append(SECTION_BREAK)
            append(oneLine(summary))
        }
        appendMapEntries(group.documentedRoles(), group.documentedGroups(), depth + 1)
    }
}

private fun MutableList<String>.addMapBullets(group: Group, indent: Int) {
    this += "${BULLET_INDENT.repeat(indent)}- ${groupLink(group)}"
    for (role in group.documentedRoles()) {
        this += "${BULLET_INDENT.repeat(indent + 1)}- ${roleLink(role)}"
    }
    for (child in group.documentedGroups()) addMapBullets(child, indent + 1)
}

private fun StringBuilder.appendGroupSection(group: Group, depth: Int) {
    append(SECTION_BREAK)
    append("#".repeat(minOf(TOP_GROUP_SECTION_HASHES + depth, DEEPEST_HEADING_HASHES)))
    append(" ")
    append(group.displayName)
    group[Summary]?.takeIf { it.isNotBlank() }?.let { summary ->
        append(SECTION_BREAK)
        append(summary)
    }
    val roles = group.documentedRoles()
    if (roles.isNotEmpty()) {
        append(SECTION_BREAK)
        append(roles.joinToString("\n") { role -> "- ${roleLink(role)}${roleSummary(role)}" })
    }
    for (child in group.documentedGroups()) appendGroupSection(child, depth + 1)
}

private fun roleSummary(role: Role): String =
    role[Summary]?.takeIf { it.isNotBlank() }?.let { SUMMARY_SEPARATOR + oneLine(it) }.orEmpty()

private fun roleLink(role: Role): String = link(role.displayName, "${role.pagePath()}$PAGE_EXTENSION")

private fun groupLink(group: Group): String = link(group.displayName, "${group.directoryPath()}/$README")

private fun Group.documentedRoles(): List<Role> = roles.filter { it.isDocumented }

private fun Group.documentedGroups(): List<Group> = groups.filter { it.isDocumented }

private const val DEEPEST_HEADING_HASHES: Int = 6

private const val TOP_MAP_HEADING_HASHES: Int = 3

private const val DEEPEST_MAP_HEADING_DEPTH: Int = DEEPEST_HEADING_HASHES - TOP_MAP_HEADING_HASHES + 1

private const val TOP_GROUP_SECTION_HASHES: Int = 2
