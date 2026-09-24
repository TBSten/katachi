package me.tbsten.katachi.docs

import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary

/**
 * The `README.md` of one container: the documentation root, or one group.
 *
 * The root and a group are written by the same function because they hold the same two things
 * -- roles of their own, and containers below them -- and a reader arriving at either is asking
 * the same question. One rule covers both: a container shows the placement of the roles it lists
 * right above, and of nothing else. So the root shows the roles written straight inside
 * `architecture { }`, which are otherwise in no tree at all, and no page ever shows a tree that
 * crosses groups -- a reader could not tell which group such a tree was about.
 *
 * The two differences are the heading, which a group takes from its `title` and the root has no
 * name to take, and [placementHeading], because "このグループの配置" is not true of the root.
 *
 * There is no body text here. A group carries no `summary` and no `description` today: the one
 * thing said about a group is its name, and inventing a paragraph for it would be katachi
 * writing documentation rather than generating it.
 */
internal fun containerPage(
    title: String,
    roles: List<Role>,
    groups: List<Group>,
    placements: Map<Role, List<Placement>>,
    placementHeading: String,
    ancestors: List<Crumb>,
): String = buildString {
    appendBreadcrumb(ancestors)
    append("# $title")
    appendRoleTable(roles)
    appendDirectoryTree(roles, placements, placementHeading)
    appendGroupList(groups)
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
 * Where the files of this container's roles go, as one tree per module.
 *
 * Straight after the table, because it answers the same question from the other side: the table
 * says what a role is and the tree says what a directory holds. The role names in it are plain
 * text -- a link inside a fenced block is not a link, and the table right above already carries
 * one per role.
 */
private fun StringBuilder.appendDirectoryTree(
    roles: List<Role>,
    placements: Map<Role, List<Placement>>,
    heading: String,
) {
    val tree = directoryTree(roles, placements)
    if (tree.isEmpty()) return
    append(SECTION_BREAK)
    append(heading)
    append(SECTION_BREAK)
    append("```\n")
    append(tree)
    append("\n```")
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
