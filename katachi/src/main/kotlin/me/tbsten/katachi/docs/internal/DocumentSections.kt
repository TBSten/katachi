package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.DocumentSection
import me.tbsten.katachi.dsl.internal.MetadataValues
import me.tbsten.katachi.dsl.internal.documentSectionBodies

internal fun StringBuilder.appendDocumentSections(metadata: MetadataValues) {
    for (root in documentSectionTree(metadata)) appendSection(root)
}

private class SectionNode(val section: DocumentSection) {
    var markdown: String? = null

    val children = LinkedHashMap<DocumentSection, SectionNode>()
}

private fun documentSectionTree(metadata: MetadataValues): List<SectionNode> {
    val roots = LinkedHashMap<DocumentSection, SectionNode>()
    for (body in metadata.documentSectionBodies()) {
        roots.nodeFor(body.section).markdown = body.markdown
    }
    return roots.values.toList()
}

private fun LinkedHashMap<DocumentSection, SectionNode>.nodeFor(
    section: DocumentSection,
): SectionNode {
    val parent = section.parent
    val siblings = if (parent == null) this else nodeFor(parent).children
    return siblings.getOrPut(section) { SectionNode(section) }
}

private fun StringBuilder.appendSection(node: SectionNode) {
    append(SECTION_BREAK)
    append("#".repeat(node.section.depth + TOP_LEVEL_SECTION_HASHES))
    append(" ")
    append(node.section.heading)
    // Trimmed like `description`, and for the same reason: a `"""` literal written in the DSL
    // opens and closes on lines of its own, and those blank lines are the quoting rather than
    // the text.
    val markdown = node.markdown?.trim()
    if (!markdown.isNullOrEmpty()) {
        append(SECTION_BREAK)
        append(markdown)
    }
    for (child in node.children.values) appendSection(child)
}

private const val TOP_LEVEL_SECTION_HASHES: Int = 2
