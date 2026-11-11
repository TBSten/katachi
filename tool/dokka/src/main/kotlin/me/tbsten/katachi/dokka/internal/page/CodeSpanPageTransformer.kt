package me.tbsten.katachi.dokka.internal.page

import me.tbsten.katachi.dokka.internal.markdown.CodeSpanText
import org.jetbrains.dokka.pages.ContentCodeInline
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.ContentText
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.pages.recursiveMapTransform
import org.jetbrains.dokka.transformers.pages.PageTransformer

/** Shows every inline code span of the HTML pages as it was written; see [CodeSpanText]. */
internal class CodeSpanPageTransformer : PageTransformer {
    override fun invoke(input: RootPageNode): RootPageNode = input.transformContentPagesTree { page ->
        page.modified(content = page.content.recursiveMapTransform<ContentCodeInline, ContentNode> { it.asWritten() })
    }

    private fun ContentCodeInline.asWritten(): ContentCodeInline {
        val parsed = children.singleOrNull() as? ContentText ?: return this
        val written = CodeSpanText.of(parsed.text)
        return if (written == parsed.text) this else copy(children = listOf(parsed.copy(text = written)))
    }
}
