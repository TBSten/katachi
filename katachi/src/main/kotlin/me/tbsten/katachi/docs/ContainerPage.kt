package me.tbsten.katachi.docs

import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary

/**
 * The `README.md` of one container: the documentation root, or one group.
 *
 * The root and a group are written by the same function because they hold the same two things
 * -- roles of their own, and containers below them -- and a reader arriving at either is asking
 * the same question. The only difference is the heading, which a group takes from its `title`
 * and the root has no name to take.
 *
 * There is no body text here. A group carries no `summary` and no `description` today: the one
 * thing said about a group is its name, and inventing a paragraph for it would be katachi
 * writing documentation rather than generating it.
 */
internal fun containerPage(
    title: String,
    roles: List<Role>,
    groups: List<Group>,
): String = buildString {
    append("# $title")
    appendRoleTable(roles)
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
