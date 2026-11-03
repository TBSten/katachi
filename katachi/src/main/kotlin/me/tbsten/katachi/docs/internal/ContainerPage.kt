package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.internal.MetadataValues

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
    append(tableRow(listOf("Role", "Summary")))
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

private fun StringBuilder.appendGroupList(groups: List<Group>) {
    if (groups.isEmpty()) return
    append(SECTION_BREAK)
    append("## Groups")
    append(SECTION_BREAK)
    append(groups.joinToString("\n") { "- ${link(it.displayName, "${it.name}/$README")}" })
}
