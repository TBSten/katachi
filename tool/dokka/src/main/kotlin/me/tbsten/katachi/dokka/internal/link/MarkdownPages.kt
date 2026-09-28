package me.tbsten.katachi.dokka.internal.link

/** What is appended to a page's URL for its Markdown version, as https://llmstxt.org/ proposes. */
internal const val MARKDOWN_SUFFIX: String = ".md"

/**
 * The Markdown version of the page at [pagePath]: `-cart/index.html` → `-cart/index.html.md`.
 *
 * An anchor is dropped, since the Markdown version of a page is one file without anchors of its own.
 */
internal fun markdownPathOf(pagePath: String): String = pagePath.substringBefore('#') + MARKDOWN_SUFFIX

/** Which version of a page the links of an llms file lead to. */
internal enum class LinkedPage {
    /** The HTML page, as Dokka writes it. */
    Html,

    /** The Markdown version next to it, see [markdownPathOf]. */
    Markdown,
    ;

    /** The path of this version of the page at [pagePath], an HTML path relative to the output root. */
    fun pathOf(pagePath: String): String = if (this == Markdown) markdownPathOf(pagePath) else pagePath
}
