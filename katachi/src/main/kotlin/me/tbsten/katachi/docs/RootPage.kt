package me.tbsten.katachi.docs

import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.MetadataValues
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary

/**
 * The `README.md` of the documentation root: the one page a reader arrives at knowing nothing.
 *
 * It answers two questions in this order, and the order is the whole design. "Where is
 * everything" comes first, as [DOCUMENT_MAP_HEADING] -- every generated page, once, as a name and
 * a link, so that a reader scanning it is reading names rather than sentences. "What is in each
 * group" comes after, one section per top level group, where the same roles are listed again with
 * the one line each of them wrote about itself.
 *
 * Saying the roles twice is deliberate. An index that carried summaries would be a page of
 * paragraphs nobody can scan, and sections without an index would make a reader open every group
 * to find out whether the page they want is in it.
 *
 * Between the two sits the placement tree, because it is the one thing on this page that the map
 * does not also say: the map is about pages, and the tree is about where the files themselves go.
 *
 * [title] and [description] arrive already chosen, from `--arg`, from the Gradle plugin, or from
 * what the definition wrote on itself -- see `roleReferenceDocuments`. The root is the one
 * container with no name of its own, so the name has to come from outside it.
 */
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

/**
 * The roles written straight into `architecture { }`, with the line of prose each of them has.
 *
 * The same shape a group's section gives its own roles, for the same reason: the map above is an
 * index and carries names only. A role that belongs to no group would otherwise be the one kind
 * whose `summary` a reader cannot see without opening its page.
 */
private fun StringBuilder.appendRootRoles(roles: List<Role>) {
    if (roles.isEmpty()) return
    append(SECTION_BREAK)
    append(ROOT_ROLES_HEADING)
    append(SECTION_BREAK)
    append(roles.joinToString("\n") { role -> "- ${roleLink(role)}${roleSummary(role)}" })
}

/**
 * Every page this run produces, as one nested index.
 *
 * Absent when the definition declared nothing at all, the same way every other section of every
 * generated page is: a heading with nothing under it says less than no heading.
 */
private fun StringBuilder.appendDocumentMap(roles: List<Role>, groups: List<Group>) {
    if (roles.isEmpty() && groups.isEmpty()) return
    append(SECTION_BREAK)
    append(DOCUMENT_MAP_HEADING)
    appendMapEntries(roles, groups, depth = 0)
}

/**
 * The roles of one container and then the containers below it, [depth] levels under the map.
 *
 * The roles come first because they are the pages of this container itself; a group heading
 * written above them would claim them.
 */
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
        appendMapEntries(group.documentedRoles(), group.documentedGroups(), depth + 1)
    }
}

/** One group of the map that ran out of heading levels, and everything below it. */
private fun MutableList<String>.addMapBullets(group: Group, indent: Int) {
    this += "${BULLET_INDENT.repeat(indent)}- ${groupLink(group)}"
    for (role in group.documentedRoles()) {
        this += "${BULLET_INDENT.repeat(indent + 1)}- ${roleLink(role)}"
    }
    for (child in group.documentedGroups()) addMapBullets(child, indent + 1)
}

/**
 * One group's section: what the group is, and what is in it.
 *
 * `description` is deliberately absent. The group's own `README.md` carries it, and repeating a
 * free-form body here would make the root page as long as all of them put together -- `summary`
 * is the one line that was written to be read somewhere else.
 *
 * A group nested inside another continues in the same section rather than starting one, because
 * it is part of what the outer group holds.
 */
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

/** The one line a role wrote about itself, or nothing — never a separator with nothing after it. */
private fun roleSummary(role: Role): String =
    role[Summary]?.takeIf { it.isNotBlank() }?.let { SUMMARY_SEPARATOR + oneLine(it) }.orEmpty()

/** A link from the root to one role's page. Every path here is written from the root. */
private fun roleLink(role: Role): String = link(role.displayName, "${role.qualifiedName}$PAGE_EXTENSION")

/** A link from the root to one group's `README.md`. */
private fun groupLink(group: Group): String = link(group.displayName, "${group.qualifiedName}/$README")

private fun Group.documentedRoles(): List<Role> = roles.filter { it.isDocumented }

private fun Group.documentedGroups(): List<Group> = groups.filter { it.isDocumented }

/** How many hashes still make a heading. A seventh is seven characters of text. */
private const val DEEPEST_HEADING_HASHES: Int = 6

/** What a top level group is written with inside the map, one level below the map's own `##`. */
private const val TOP_MAP_HEADING_HASHES: Int = 3

/**
 * The depth at which the map stops writing headings and starts indenting.
 *
 * A container at this depth would give its groups a seventh hash. Counted rather than written
 * out so that the two constants cannot drift.
 */
private const val DEEPEST_MAP_HEADING_DEPTH: Int = DEEPEST_HEADING_HASHES - TOP_MAP_HEADING_HASHES + 1

/** What a top level group's own section is written with, one level below the page's title. */
private const val TOP_GROUP_SECTION_HASHES: Int = 2
