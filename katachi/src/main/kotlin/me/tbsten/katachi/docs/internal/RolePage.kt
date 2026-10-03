package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.Role

/**
 * The page of one role: what it is, where its files may live, what is asked of them.
 *
 * Every section is a function of its own, and a section with nothing to say appends nothing --
 * not an empty heading. A section katachi does not write itself arrives the same way: the
 * constraints of the role write their own ([constraintSections], each starting with its own
 * heading, see `FileConstraint.documentSection`), placed after the list of the constraints' names
 * and before the examples. The last call is the other section katachi does not name: whatever the
 * definition declared for itself, written after everything katachi knows how to assemble.
 *
 * The body is assembled with plain string building rather than through a model of the document.
 * A model would have to be designed for sections nobody has written yet, and the sections do not
 * share enough to be worth one: a table, a bullet list and a block of the user's own Markdown.
 */
internal fun rolePage(
    role: Role,
    placements: List<Placement>,
    constraintNames: List<String>,
    constraintSections: List<String>,
    ancestors: List<Crumb>,
): String = buildString {
    appendBreadcrumb(ancestors)
    appendTitle(role.displayName, role.metadata)
    appendDescription(role.metadata)
    appendPlacements(placements)
    appendFileConstraints(constraintNames)
    appendConstraintSections(constraintSections)
    appendExamples(role)
    appendDocumentSections(role.metadata)
    appendLine()
}

/**
 * Where the role's files may live: one row per declared pattern.
 *
 * The Module column appears only when some row has a module to show. Without one, every cell in
 * the column would be empty, so the table drops it and starts at Path.
 */
private fun StringBuilder.appendPlacements(placements: List<Placement>) {
    if (placements.isEmpty()) return
    val withModule = placements.any { it.module != null }
    append(SECTION_BREAK)
    append("## Placement")
    append(SECTION_BREAK)
    if (withModule) {
        append(tableRow(listOf("Module", "Path", "When to use")))
        append("\n|---|---|---|")
    } else {
        append(tableRow(listOf("Path", "When to use")))
        append("\n|---|---|")
    }
    for (placement in placements) {
        append("\n")
        val cells = listOfNotNull(
            if (withModule) placement.module?.let { "`$it`" }.orEmpty() else null,
            "`${placement.path}`",
            placement.description.orEmpty(),
        )
        append(tableRow(cells))
    }
}

/**
 * The named constraints, as they were named.
 *
 * Only the named ones reach here -- an unnamed constraint has nothing to print -- which is the
 * point of being able to name one: writing a name is what puts a rule on the page.
 */
private fun StringBuilder.appendFileConstraints(constraintNames: List<String>) {
    if (constraintNames.isEmpty()) return
    append(SECTION_BREAK)
    append("## Constraints")
    append(SECTION_BREAK)
    append(constraintNames.joinToString("\n") { "- $it" })
}

/** The sections the role's constraints wrote about themselves, as they wrote them. */
private fun StringBuilder.appendConstraintSections(sections: List<String>) {
    for (section in sections) {
        append(SECTION_BREAK)
        append(section)
    }
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
    append("## Examples")
    append(SECTION_BREAK)
    append(
        examples.joinToString("\n") {
            val description = it.description
            if (description == null) "- `${it.name}`" else "- `${it.name}` ... $description"
        },
    )
}
