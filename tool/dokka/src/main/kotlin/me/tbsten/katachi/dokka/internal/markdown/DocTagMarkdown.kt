package me.tbsten.katachi.dokka.internal.markdown

import org.jetbrains.dokka.base.translators.documentables.firstParagraphBrief
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.doc.A
import org.jetbrains.dokka.model.doc.B
import org.jetbrains.dokka.model.doc.BlockQuote
import org.jetbrains.dokka.model.doc.Br
import org.jetbrains.dokka.model.doc.CodeBlock
import org.jetbrains.dokka.model.doc.CodeInline
import org.jetbrains.dokka.model.doc.CustomDocTag
import org.jetbrains.dokka.model.doc.Div
import org.jetbrains.dokka.model.doc.DocTag
import org.jetbrains.dokka.model.doc.DocumentationLink
import org.jetbrains.dokka.model.doc.Em
import org.jetbrains.dokka.model.doc.H1
import org.jetbrains.dokka.model.doc.H2
import org.jetbrains.dokka.model.doc.H3
import org.jetbrains.dokka.model.doc.H4
import org.jetbrains.dokka.model.doc.H5
import org.jetbrains.dokka.model.doc.H6
import org.jetbrains.dokka.model.doc.HorizontalRule
import org.jetbrains.dokka.model.doc.I
import org.jetbrains.dokka.model.doc.Li
import org.jetbrains.dokka.model.doc.Ol
import org.jetbrains.dokka.model.doc.P
import org.jetbrains.dokka.model.doc.Pre
import org.jetbrains.dokka.model.doc.Strikethrough
import org.jetbrains.dokka.model.doc.Strong
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.doc.Ul

/**
 * Writes KDoc, as Dokka parsed it into `DocTag`s, back out as Markdown.
 *
 * The llms files are Markdown, and the HTML run they are written from has no Markdown renderer
 * (Dokka's GFM output replaces the whole renderer), so this small writer does it. Tags it does
 * not know keep their text and lose their markup, so an unknown tag degrades instead of failing.
 *
 * [resolve] turns the target of a `[Declaration]` link into the link to write, or null when the
 * declaration has no page; such a link is written by [unresolved] from its label, as the label
 * alone by default. [headingOffset] pushes every heading that many levels down, for KDoc that is
 * embedded under headings of its own; levels past 6 stay at 6.
 */
internal class DocTagMarkdown(
    private val resolve: (DRI) -> String?,
    private val unresolved: (label: String, dri: DRI) -> String = { label, _ -> label },
    private val headingOffset: Int = 0,
) {
    /** [tag] as Markdown blocks separated by blank lines, without surrounding blank lines. */
    fun block(tag: DocTag): String = blocksOf(listOf(tag)).trim().replace(BLANK_LINES, "\n\n")

    /** The first paragraph of [tag] on one line, for a list item; empty when there is none. */
    fun brief(tag: DocTag): String = firstParagraphBrief(tag)?.let { oneLine(it) }.orEmpty()

    /** [tag] as inline Markdown on one line. */
    fun oneLine(tag: DocTag): String = inlineOf(tag.children.ifEmpty { listOf(tag) }).replace(WHITESPACE, " ").trim()

    private fun blocksOf(tags: List<DocTag>): String = buildString {
        val pending = mutableListOf<DocTag>()
        fun flush() {
            val text = inlineOf(pending).trim()
            if (text.isNotEmpty()) append(text).append("\n\n")
            pending.clear()
        }
        tags.forEach { tag ->
            if (tag.isBlock()) {
                flush()
                append(blockOf(tag))
            } else {
                pending += tag
            }
        }
        flush()
    }

    private fun blockOf(tag: DocTag): String = when (tag) {
        is CustomDocTag, is Div -> blocksOf(tag.children)
        is P -> inlineOf(tag.children).trim().let { if (it.isEmpty()) "" else "$it\n\n" }
        is H1 -> heading(1, tag)
        is H2 -> heading(2, tag)
        is H3 -> heading(3, tag)
        is H4 -> heading(4, tag)
        is H5 -> heading(5, tag)
        is H6 -> heading(6, tag)
        is CodeBlock -> fence(tag.params["lang"].orEmpty(), tag.children)
        is Pre -> fence("", tag.children)
        is Ul -> listItems(tag.children) { "- " } + "\n"
        is Ol -> {
            val start = tag.params["start"]?.toIntOrNull() ?: 1
            listItems(tag.children) { index -> "${start + index}. " } + "\n"
        }
        is BlockQuote -> blocksOf(tag.children).trim().lines().joinToString("\n") { "> $it".trimEnd() } + "\n\n"
        is HorizontalRule -> "---\n\n"
        else -> blocksOf(tag.children)
    }

    private fun heading(level: Int, tag: DocTag): String =
        "#".repeat((level + headingOffset).coerceIn(1, MAX_HEADING_LEVEL)) + " " + oneLine(tag) + "\n\n"

    private fun fence(language: String, children: List<DocTag>): String {
        val code = codeOf(children).trimEnd()
        val fence = if (code.contains("```")) "````" else "```"
        return "$fence$language\n$code\n$fence\n\n"
    }

    private fun listItems(items: List<DocTag>, marker: (Int) -> String): String = buildString {
        items.filterIsInstance<Li>().forEachIndexed { index, item ->
            val prefix = marker(index)
            val body = blocksOf(item.children).trim().replace(BLANK_LINES, "\n")
            val indent = " ".repeat(prefix.length)
            append(prefix)
            append(body.lines().mapIndexed { line, text -> if (line == 0) text else "$indent$text".trimEnd() }.joinToString("\n"))
            append('\n')
        }
    }

    private fun inlineOf(tags: List<DocTag>): String = tags.joinToString("") { inline(it) }

    private fun inline(tag: DocTag): String = when (tag) {
        is Text -> tag.body
        is Br -> "\n"
        is CodeInline -> code(codeOf(tag.children))
        is B, is Strong -> wrap("**", tag)
        is I, is Em -> wrap("*", tag)
        is Strikethrough -> wrap("~~", tag)
        is A -> link(inlineOf(tag.children), tag.params["href"])
        is DocumentationLink -> inlineOf(tag.children).let { label ->
            resolve(tag.dri)?.takeIf { it.isNotBlank() }?.let { link(label, it) } ?: unresolved(label, tag.dri)
        }
        // A block inside inline content (a list inside a table cell, say) keeps only its text.
        else -> inlineOf(tag.children)
    }

    private fun wrap(marker: String, tag: DocTag): String =
        inlineOf(tag.children).let { if (it.isBlank()) it else "$marker$it$marker" }

    private fun link(text: String, target: String?): String = when {
        target.isNullOrBlank() -> text
        text.isBlank() -> "<$target>"
        else -> "[$text]($target)"
    }

    private fun code(text: String): String {
        val fence = if (text.contains('`')) "``" else "`"
        val padding = if (text.startsWith('`') || text.endsWith('`')) " " else ""
        return "$fence$padding$text$padding$fence"
    }

    /** The text of code, where line breaks are the only markup that matters. */
    private fun codeOf(tags: List<DocTag>): String = tags.joinToString("") { tag ->
        when (tag) {
            is Text -> tag.body + codeOf(tag.children)
            is Br -> "\n"
            else -> codeOf(tag.children)
        }
    }

    private fun DocTag.isBlock(): Boolean = when (this) {
        is P, is H1, is H2, is H3, is H4, is H5, is H6, is CodeBlock, is Pre, is Ul, is Ol,
        is BlockQuote, is HorizontalRule, is CustomDocTag, is Div,
        -> true
        else -> false
    }

    private companion object {
        const val MAX_HEADING_LEVEL = 6
        val BLANK_LINES = Regex("\n{3,}")
        val WHITESPACE = Regex("\\s+")
    }
}
