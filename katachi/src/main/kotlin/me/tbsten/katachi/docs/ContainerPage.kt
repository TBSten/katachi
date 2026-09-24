package me.tbsten.katachi.docs

import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary

/**
 * The `README.md` of one container: the documentation root, or one group.
 *
 * The root and a group are written by the same function because they hold the same two things
 * -- roles of their own, and containers below them -- and a reader arriving at either is asking
 * the same question. The heading is one difference, which a group takes from its `title` and the
 * root has no name to take; the placement tree is the other, and the root is passed no
 * [placements] because that tree is a group's.
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
): String = buildString {
    append("# $title")
    appendRoleTable(roles)
    appendDirectoryTree(roles, placements)
    appendGroupList(groups)
    appendLine()
}

/**
 * The roles this container holds, with the one line each of them gets.
 *
 * `summary` is a table cell, which is exactly why the DSL keeps it apart from `description`:
 * this is the place a paragraph break would break.
 */
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
private fun StringBuilder.appendDirectoryTree(roles: List<Role>, placements: Map<Role, List<Placement>>) {
    val tree = directoryTree(roles, placements)
    if (tree.isEmpty()) return
    append(SECTION_BREAK)
    append("## このグループの配置")
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
