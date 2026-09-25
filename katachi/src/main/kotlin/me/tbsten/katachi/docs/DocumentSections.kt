package me.tbsten.katachi.docs

import me.tbsten.katachi.dsl.DocumentSection
import me.tbsten.katachi.dsl.internal.MetadataValues
import me.tbsten.katachi.dsl.internal.documentSectionBodies

/**
 * The sections the definition declared for itself, after every section katachi assembles.
 *
 * Last on the page because the sections katachi writes answer questions it can answer -- where
 * the files go, what is asked of them, what one looks like -- and a reader who arrived at the
 * page is asking those first. What a team adds is about the same subject but is theirs, and
 * putting it above would mean katachi deciding that this team's "how it is tested" outranks the
 * placement table on every page it appears on.
 *
 * Their order among themselves is the order the definition wrote them in, which the metadata
 * kept. A section written below another one is an exception only in that it goes where its
 * parent does: the parent takes the place of whichever of its children was written first, so
 * writing a child before its parent moves the whole group up rather than splitting it.
 */
internal fun StringBuilder.appendDocumentSections(metadata: MetadataValues) {
    for (root in documentSectionTree(metadata)) appendSection(root)
}

/** One section that will be written out, and the sections written below it. */
private class SectionNode(val section: DocumentSection) {
    /** The Markdown written under it, or `null` when only something below it was written. */
    var markdown: String? = null

    /** A `LinkedHashMap`, so children keep the order they first appeared in. */
    val children = LinkedHashMap<DocumentSection, SectionNode>()
}

/**
 * The written sections as a tree, in the order they first appeared.
 *
 * A section reaches the tree when it was written, or when something below it was: that is what
 * makes a parent with nothing of its own still get a heading, and what keeps a section nobody
 * wrote anything under off the page entirely.
 */
private fun documentSectionTree(metadata: MetadataValues): List<SectionNode> {
    val roots = LinkedHashMap<DocumentSection, SectionNode>()
    for (body in metadata.documentSectionBodies()) {
        roots.nodeFor(body.section).markdown = body.markdown
    }
    return roots.values.toList()
}

/** The node of [section] below these roots, creating it and every node above it as needed. */
private fun LinkedHashMap<DocumentSection, SectionNode>.nodeFor(
    section: DocumentSection,
): SectionNode {
    val parent = section.parent
    val siblings = if (parent == null) this else nodeFor(parent).children
    return siblings.getOrPut(section) { SectionNode(section) }
}

/** One section and everything below it, the heading level following the depth. */
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

/**
 * What a top level section is written with. The page's own title is the one `#`, so a section of
 * the definition starts one level below it.
 */
private const val TOP_LEVEL_SECTION_HASHES: Int = 2
