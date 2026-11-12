package me.tbsten.katachi.dokka.internal.fragment

import me.tbsten.katachi.dokka.internal.markdown.CodeSpanText
import org.jetbrains.dokka.base.translators.documentables.firstParagraphBrief
import org.jetbrains.dokka.model.doc.Br
import org.jetbrains.dokka.model.doc.CodeInline
import org.jetbrains.dokka.model.doc.DocTag
import org.jetbrains.dokka.model.doc.Text

/**
 * A run of summary text, either prose or inline code.
 *
 * The form a summary takes once it leaves the run that parsed it: a `DocTag` cannot be written
 * into the fragment, and the aggregating run has no documentables to resolve links against.
 */
internal data class SummarySegment(
    val text: String,
    val code: Boolean,
)

/**
 * The first paragraph of [this], flattened to prose and inline code.
 *
 * Links keep their text and lose their target, and every other markup is dropped. Adjacent runs
 * of the same kind are joined, so the result does not depend on how the parser split the text.
 */
internal fun DocTag.toSummarySegments(): List<SummarySegment> {
    val brief = firstParagraphBrief(this) ?: return emptyList()
    val segments = mutableListOf<SummarySegment>()
    fun append(text: String, code: Boolean) {
        if (text.isEmpty()) return
        val last = segments.lastOrNull()
        if (last != null && last.code == code) {
            segments[segments.lastIndex] = last.copy(text = last.text + text)
        } else {
            segments += SummarySegment(text, code)
        }
    }

    fun visit(tag: DocTag) {
        when (tag) {
            is Text -> append(tag.body, code = false)
            is Br -> append(" ", code = false)
            is CodeInline -> append(CodeSpanText.of(codeOf(tag)), code = true)
            else -> tag.children.forEach(::visit)
        }
    }
    visit(brief)
    return segments
        .map { if (it.code) it else it.copy(text = it.text.replace(WHITESPACE, " ")) }
        .filter { it.text.isNotBlank() || it.code }
}

/** The text of an inline code span, where a line break is a space. */
private fun codeOf(tag: DocTag): String = tag.children.joinToString("") { child ->
    when (child) {
        is Text -> child.body
        is Br -> " "
        else -> codeOf(child)
    }
}

private val WHITESPACE = Regex("\\s+")
