package me.tbsten.katachi.dokka.internal.summary

import me.tbsten.katachi.dokka.internal.featured.hasText
import org.jetbrains.dokka.base.translators.documentables.firstParagraphBrief
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.CustomTagWrapper
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocTag
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.P
import org.jetbrains.dokka.model.doc.Property
import org.jetbrains.dokka.model.doc.Text

/** The KDoc tag whose text replaces the summary the llms files show, without its `@`. */
internal const val LLM_TAG: String = "llm"

/**
 * The short summary shown next to a link: in the llms files, in the Markdown pages and in the
 * Featured sections of the HTML.
 *
 * The text after `@llm` when the KDoc has one, taken as written; otherwise the first paragraph of
 * the description (or of the `@property` a class wrote for it), cut after one or two sentences.
 * Package and module docs come from Dokka's `includes`, so a package documented there gets its
 * first paragraph too.
 *
 * The result is a paragraph of `DocTag`s, so each output writes it with its own renderer and
 * links stay links.
 */
internal object Summaries {
    fun of(documentation: DocumentationNode?): DocTag? {
        val tags = documentation?.children.orEmpty()
        tags.filterIsInstance<CustomTagWrapper>()
            .firstOrNull { it.name == LLM_TAG }
            ?.root?.let(::firstParagraphBrief)
            ?.takeIf { it.hasText() }
            ?.let { return paragraphOf(it) }
        val description = tags.filterIsInstance<Description>().firstOrNull()?.root
            // A property documented only by its class's `@property`, which Dokka hands down to it.
            ?: tags.filterIsInstance<Property>().firstOrNull()?.root
            ?: return null
        return firstParagraphBrief(description)?.takeIf { it.hasText() }?.let(SentenceCut::cut)
    }

    /** The first summary among [documentations], for a page that shows several declarations. */
    fun firstOf(documentations: List<DocumentationNode?>): DocTag? = documentations.firstNotNullOfOrNull(::of)
}

/** The KDoc of the source set Dokka shows first: the `expect` one, else the first. */
internal fun documentationOf(documentable: Documentable): DocumentationNode? {
    val preferred = documentable.expectPresentInSet ?: documentable.sourceSets.firstOrNull()
    return documentable.documentation[preferred] ?: documentable.documentation.values.firstOrNull()
}

/**
 * Cuts a paragraph after its first sentence, or after the second when the first is short.
 *
 * Only plain text is cut. Inline code, links and emphasis are kept whole, so a period inside
 * `` `a.b()` `` never ends a sentence and no summary stops inside a code span or a link.
 */
internal object SentenceCut {
    fun cut(paragraph: DocTag): DocTag {
        val children = paragraphOf(paragraph).children
        val kept = mutableListOf<DocTag>()
        var length = 0
        var sentences = 0
        children.forEachIndexed { index, child ->
            if (child !is Text || child.children.isNotEmpty()) {
                kept += child
                length += textLengthOf(child)
                return@forEachIndexed
            }
            val body = child.body
            SENTENCE_END.findAll(body).forEach { match ->
                val end = match.range.last + 1
                // A period at the very end of a text run ends a sentence only if what follows
                // starts a new one: `see Foo.` + `bar` is one word split by the parser.
                if (end == body.length && !startsNewSentence(children.getOrNull(index + 1))) return@forEach
                sentences++
                if (sentences >= MAX_SENTENCES || length + end >= ENOUGH_LENGTH) {
                    kept += Text(body.substring(0, end).trimEnd())
                    return P(kept)
                }
            }
            kept += child
            length += body.length
        }
        return P(kept)
    }

    private fun startsNewSentence(next: DocTag?): Boolean =
        next == null || (next is Text && next.body.firstOrNull()?.isWhitespace() != false)

    private fun textLengthOf(tag: DocTag): Int = if (tag is Text) tag.body.length else tag.children.sumOf(::textLengthOf)

    /** A first sentence at least this long is summary enough; a shorter one takes the next along. */
    private const val ENOUGH_LENGTH = 60
    private const val MAX_SENTENCES = 2

    // ASCII punctuation only ends a sentence before whitespace (so `1.5` and `a.b` do not);
    // the full-width ones end it wherever they are.
    private val SENTENCE_END = Regex("""[.!?](?=\s|$)|[。！？]""")
}

/** [tag] as a paragraph: itself when it is one, else a paragraph holding it. */
private fun paragraphOf(tag: DocTag): P = tag as? P ?: P(listOf(tag))
