package me.tbsten.katachi.test.dokka

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * One node of Dokka's sidebar as `navigation.html` renders it: a `toc--part` with its row's link
 * and the parts nested in it.
 */
internal data class SidebarNode(
    val name: String,
    /** Where the row links to; empty for a label-only package node, which is not a link. */
    val href: String,
    /** The CSS classes of the node's icon, without the shared `toc--icon`; empty without one. */
    val icon: Set<String>,
    val children: List<SidebarNode>,
    /** Whether the row is a label (`span.toc--link`) instead of a link. */
    val labelOnly: Boolean = false,
)

/** The top-level nodes of [html], the contents of a `navigation.html`. */
internal fun sidebarOf(html: String): List<SidebarNode> =
    Jsoup.parseBodyFragment(html).body().let { body ->
        (body.selectFirst("div.sideMenu") ?: body).children().filter { it.hasClass(TOC_PART) }.map(::nodeOf)
    }

/** The child of [this] named [name], or an error listing the names there are. */
internal fun List<SidebarNode>.named(name: String): SidebarNode =
    firstOrNull { it.name == name } ?: error("No sidebar node named $name among ${map { it.name }}")

private fun nodeOf(part: Element): SidebarNode {
    val link = part.children().first { it.hasClass("toc--row") }.selectFirst(".toc--link")
        ?: error("A sidebar node without a link or a label: $part")
    return SidebarNode(
        // Dokka breaks long names into spans with <wbr> between them, which text() joins.
        name = link.text(),
        href = link.attr("href"),
        icon = link.selectFirst("span.toc--icon")?.classNames()?.minus("toc--icon").orEmpty(),
        children = part.children().filter { it.hasClass(TOC_PART) }.map(::nodeOf),
        labelOnly = link.tagName() != "a",
    )
}

private const val TOC_PART = "toc--part"

/** Every node of this forest, parents before their children. */
internal fun List<SidebarNode>.flatten(): List<SidebarNode> = flatMap { listOf(it) + it.children.flatten() }
