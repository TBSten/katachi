package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.Role
import java.util.IdentityHashMap

internal fun rolePage(
    role: Role,
    placements: List<Placement>,
    constraints: ConstraintDocumentation,
    ancestors: List<Crumb>,
): String = buildString {
    val head = buildString {
        appendBreadcrumb(ancestors)
        appendTitle(role.displayName, role.metadata)
        appendDescription(role.metadata)
        appendPlacements(placements)
    }
    append(head)
    appendFileConstraints(constraints.items, sectionSlugsOf(head, constraints))
    appendConstraintSections(constraints.sections)
    appendExamples(role)
    appendDocumentSections(role.metadata)
    appendLine()
}

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

private fun sectionSlugsOf(
    head: String,
    constraints: ConstraintDocumentation,
): Map<ConstraintSection, String> {
    val slugger = HeadingSlugger()
    headingTitles(head).forEach(slugger::next)
    if (constraints.items.isNotEmpty()) slugger.next("Constraints")
    val slugs = IdentityHashMap<ConstraintSection, String>()
    for (section in constraints.sections) {
        val ids = headingTitles(section.markdown).map(slugger::next)
        ids.firstOrNull()?.takeIf { it.isNotEmpty() }?.let { slugs[section] = it }
    }
    return slugs
}

private fun StringBuilder.appendFileConstraints(
    items: List<ConstraintListItem>,
    slugs: Map<ConstraintSection, String>,
) {
    if (items.isEmpty()) return
    append(SECTION_BREAK)
    append("## Constraints")
    append(SECTION_BREAK)
    append(
        items.joinToString("\n") { item ->
            val slug = item.section?.let { slugs[it] }
            if (slug == null) "- ${item.name}" else "- [${linkText(item.name)}](#$slug)"
        },
    )
}

private fun linkText(name: String): String = name.replace(LINK_TEXT_SPECIAL) { "\\${it.value}" }

private val LINK_TEXT_SPECIAL = Regex("""[\\\[\]`*_<>]""")

private fun StringBuilder.appendConstraintSections(sections: List<ConstraintSection>) {
    for (section in sections) {
        append(SECTION_BREAK)
        append(section.markdown)
    }
}

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
