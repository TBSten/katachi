package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.Role

/**
 * The page of one role: what it is, where its files may live, what is asked of them.
 *
 * Every section is a function of its own, and a section with nothing to say appends nothing --
 * not an empty heading. Two more sections are known to be coming (the declarations a role
 * allows, and the roles it may depend on), and both arrive the same way: one more function in
 * the list below, silent until the declaration that feeds it exists. The last call is the one
 * section katachi does not name: whatever the definition declared for itself, written after
 * everything katachi knows how to assemble.
 *
 * The body is assembled with plain string building rather than through a model of the document.
 * A model would have to be designed for sections nobody has written yet, and the sections do not
 * share enough to be worth one: a table, a bullet list and a block of the user's own Markdown.
 */
internal fun rolePage(
    role: Role,
    placements: List<Placement>,
    constraintNames: List<String>,
    ancestors: List<Crumb>,
): String = buildString {
    appendBreadcrumb(ancestors)
    appendTitle(role.displayName, role.metadata)
    appendDescription(role.metadata)
    appendPlacements(placements)
    appendConstraints(constraintNames)
    appendExamples(role)
    appendDocumentSections(role.metadata)
    appendLine()
}

/** Where the role's files may live: one row per declared pattern. */
private fun StringBuilder.appendPlacements(placements: List<Placement>) {
    if (placements.isEmpty()) return
    append(SECTION_BREAK)
    append("## 配置場所")
    append(SECTION_BREAK)
    append(tableRow(listOf("モジュール", "パス", "使い分け")))
    append("\n|---|---|---|")
    for (placement in placements) {
        append("\n")
        append(
            tableRow(
                listOf(
                    placement.module?.let { "`$it`" }.orEmpty(),
                    "`${placement.path}`",
                    placement.description.orEmpty(),
                ),
            ),
        )
    }
}

/**
 * The named constraints, as they were named.
 *
 * Only the named ones reach here -- an unnamed constraint has nothing to print -- which is the
 * point of being able to name one: writing a name is what puts a rule on the page.
 */
private fun StringBuilder.appendConstraints(constraintNames: List<String>) {
    if (constraintNames.isEmpty()) return
    append(SECTION_BREAK)
    append("## 制約")
    append(SECTION_BREAK)
    append(constraintNames.joinToString("\n") { "- $it" })
}

/**
 * The concrete examples, as `name ... description`.
 *
 * An example with no description prints its name alone -- no `...` and nothing after it, since
 * there is nothing to separate it from.
 */
private fun StringBuilder.appendExamples(role: Role) {
    val examples = role[Examples].orEmpty()
    if (examples.isEmpty()) return
    append(SECTION_BREAK)
    append("## 例")
    append(SECTION_BREAK)
    append(
        examples.joinToString("\n") {
            val description = it.description
            if (description == null) "- `${it.name}`" else "- `${it.name}` ... $description"
        },
    )
}
