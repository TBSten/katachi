package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.internal.MetadataValues

/**
 * The `README.md` of one group.
 *
 * One rule decides what is on it: a group shows the placement of the roles it lists right above,
 * and of nothing else. No page ever shows a tree that crosses groups -- a reader could not tell
 * which group such a tree was about.
 *
 * The root is written by [rootPage] instead. The two pages once shared this function, and they
 * stopped when the root became an index of the whole output: a group answers "what is in here",
 * while the root answers "where is everything", and only the first is a question about a
 * container.
 *
 * What the group says about itself comes from [metadata] and is written exactly where a role
 * page writes it: `summary` under the heading, `description` below that, and the sections the
 * definition declared for itself last of all. A group and a role are two things a reader arrives
 * at and asks "what is this" about, so they answer in one vocabulary -- see [appendTitle].
 */
internal fun containerPage(
    title: String,
    metadata: MetadataValues,
    roles: List<Role>,
    groups: List<Group>,
    placements: Map<Role, List<Placement>>,
    ancestors: List<Crumb>,
): String = buildString {
    appendBreadcrumb(ancestors)
    appendTitle(title, metadata)
    appendDescription(metadata)
    appendRoleTable(roles)
    appendDirectoryTree(roles, placements, GROUP_PLACEMENT_HEADING)
    appendGroupList(groups)
    appendDocumentSections(metadata)
    appendLine()
}

private fun StringBuilder.appendRoleTable(roles: List<Role>) {
    if (roles.isEmpty()) return
    append(SECTION_BREAK)
    append(tableRow(listOf("役割", "概要")))
    append("\n|---|---|")
    for (role in roles) {
        append("\n")
        append(
            tableRow(
                listOf(
                    link(role.displayName, "${role.name}$PAGE_EXTENSION"),
                    role[Summary].orEmpty(),
                ),
            ),
        )
    }
}

/**
 * The containers below this one, as links to their own `README.md`.
 *
 * A list rather than a table: a group has a name and nothing else to put in a second column.
 * Without it a nested group would be written out and never linked to from anywhere, which is a
 * page that exists and cannot be found.
 */
private fun StringBuilder.appendGroupList(groups: List<Group>) {
    if (groups.isEmpty()) return
    append(SECTION_BREAK)
    append("## グループ")
    append(SECTION_BREAK)
    append(groups.joinToString("\n") { "- ${link(it.displayName, "${it.name}/$README")}" })
}
