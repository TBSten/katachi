package me.tbsten.katachi.dokka.llms.markdown

import me.tbsten.katachi.dokka.featured.FEATURED_TAG
import me.tbsten.katachi.dokka.llms.summary.LLM_TAG
import org.jetbrains.dokka.model.doc.Author
import org.jetbrains.dokka.model.doc.Constructor
import org.jetbrains.dokka.model.doc.CustomTagWrapper
import org.jetbrains.dokka.model.doc.Deprecated
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Param
import org.jetbrains.dokka.model.doc.Property
import org.jetbrains.dokka.model.doc.Receiver
import org.jetbrains.dokka.model.doc.Return
import org.jetbrains.dokka.model.doc.Sample
import org.jetbrains.dokka.model.doc.See
import org.jetbrains.dokka.model.doc.Since
import org.jetbrains.dokka.model.doc.Suppress
import org.jetbrains.dokka.model.doc.TagWrapper
import org.jetbrains.dokka.model.doc.Throws
import org.jetbrains.dokka.model.doc.Version

/**
 * A whole KDoc comment as Markdown: the description as it is, then its block tags as a list.
 *
 * `@featured` and `@llm` are left out: they say where a declaration is listed and how, not
 * what it does.
 */
internal class DocumentationMarkdown(private val markdown: DocTagMarkdown) {
    fun of(documentation: DocumentationNode?): String {
        val tags = documentation?.children.orEmpty()
        val description = tags.filterIsInstance<Description>()
            .joinToString("\n\n") { markdown.block(it.root) }
        val deprecated = tags.filterIsInstance<Deprecated>().joinToString("\n\n") {
            "**Deprecated**: " + markdown.oneLine(it.root)
        }
        val params = tags.filterIsInstance<Param>().map { item("`${it.name}`", it) }
        val list = buildList {
            if (params.isNotEmpty()) add("- **Parameters**\n" + params.joinToString("\n") { "  - $it" })
            tags.forEach { tag -> blockTag(tag)?.let(::add) }
        }
        return listOf(deprecated, description, list.joinToString("\n"))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun blockTag(tag: TagWrapper): String? = when (tag) {
        is Return -> "- " + item("**Returns**", tag)
        is Receiver -> "- " + item("**Receiver**", tag)
        is Constructor -> "- " + item("**Constructor**", tag)
        is Property -> "- " + item("**Property** `${tag.name}`", tag)
        is Throws -> "- " + item("**Throws** `${tag.name}`", tag)
        is See -> "- " + item("**See also** `${tag.name}`", tag)
        is Sample -> "- **Sample** `${tag.name}`"
        is Since -> "- " + item("**Since**", tag)
        is Author -> "- " + item("**Author**", tag)
        is Version -> "- " + item("**Version**", tag)
        is CustomTagWrapper -> if (tag.name in LISTING_TAGS) null else "- " + item("**${tag.name}**", tag)
        // Described separately above, or not part of the documentation.
        is Description, is Param, is Deprecated, is Suppress -> null
    }

    private companion object {
        val LISTING_TAGS = setOf(FEATURED_TAG, LLM_TAG)
    }

    private fun item(label: String, tag: TagWrapper): String {
        val text = markdown.oneLine(tag.root)
        return if (text.isEmpty()) label else "$label: $text"
    }
}
