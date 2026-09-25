package me.tbsten.katachi.test.dokka

import org.jsoup.Jsoup

/** One row of a "Featured" section of a page, as Dokka renders its tables. */
internal data class FeaturedSectionRow(val name: String, val href: String, val summary: String)

/**
 * The rows of the section headed [title] on the page [html], or null when the page has no such
 * section. The section is the `h2` Dokka renders for a level-2 header, followed by its table.
 */
internal fun featuredSectionOf(html: String, title: String = "Featured"): List<FeaturedSectionRow>? {
    val header = Jsoup.parse(html).select("h2").firstOrNull { it.text() == title } ?: return null
    val table = header.nextElementSibling()?.takeIf { it.hasClass("table") } ?: return emptyList()
    return table.children().filter { it.hasClass("table-row") }.map { row ->
        val link = row.selectFirst("a") ?: error("A Featured row without a link: $row")
        FeaturedSectionRow(
            name = link.text(),
            href = link.attr("href"),
            summary = row.selectFirst(".brief-comment")?.text().orEmpty(),
        )
    }
}

/** The text of the headers of [html], in page order: where each section sits. */
internal fun headersOf(html: String): List<String> =
    Jsoup.parse(html).select("h2, h3").map { it.text() }
