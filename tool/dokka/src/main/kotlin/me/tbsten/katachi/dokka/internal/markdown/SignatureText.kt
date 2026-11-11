package me.tbsten.katachi.dokka.internal.markdown

import org.jetbrains.dokka.pages.ContentBreakLine
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.ContentText
import org.jetbrains.dokka.pages.TextStyle
import org.jetbrains.dokka.model.withDescendants

/**
 * The text of a signature Dokka's `SignatureProvider` built, as it reads on the page.
 *
 * Only the first `ContentKind.Symbol` group is read: a declaration shown in several source sets
 * gets one per source set, and they read the same for the purpose of the llms files. Text leaves
 * are joined as they are, and a line break or the end of a block becomes a newline, so annotations
 * stay on their own line.
 */
internal object SignatureText {
    fun of(nodes: List<ContentNode>): String? {
        val symbol = nodes.asSequence()
            .flatMap { it.withDescendants() }
            .firstOrNull { it.dci.kind == ContentKind.Symbol }
            ?: return null
        val text = buildString { appendText(symbol) }
        return text.lines()
            .map { line -> line.replace(SPACES, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }
    }

    /** [signature] on one line, for a list item. */
    fun oneLine(signature: String): String = signature.replace(WHITESPACE, " ").trim()

    private fun StringBuilder.appendText(node: ContentNode) {
        when (node) {
            is ContentText -> append(node.text)
            is ContentBreakLine -> append('\n')
            else -> {
                node.children.forEach { appendText(it) }
                // An annotation is a block of its own, with no line break after it.
                if (TextStyle.Block in node.style) append('\n')
            }
        }
    }

    private val SPACES = Regex("[ \\t]+")
    private val WHITESPACE = Regex("\\s+")
}
