package me.tbsten.katachi.dokka.internal.markdown

/**
 * The text of an inline code span as its author wrote it, and the span to write it back with.
 *
 * Dokka's Markdown parser takes exactly one backtick off each end of a code span, whatever the
 * length of its delimiter. A span that holds a backtick has to be delimited with two or more
 * (`` `val` `` is written with two, padded with a space), so its text arrives with the rest of
 * the delimiter and the padding still on it.
 */
internal object CodeSpanText {
    /** [parsed], the text Dokka made of a code span, without what is left of its delimiter. */
    fun of(parsed: String): String {
        val delimiter = parsed.takeWhile { it == '`' }.length
        if (delimiter == 0 || parsed.length <= delimiter * 2 || !parsed.endsWith("`".repeat(delimiter))) return parsed
        val content = parsed.substring(delimiter, parsed.length - delimiter)
        // CommonMark takes one space off each end when both ends have one and the text is not blank.
        val padded = content.startsWith(' ') && content.endsWith(' ') && content.isNotBlank()
        return if (padded) content.substring(1, content.length - 1) else content
    }

    /** [text] as a Markdown code span, delimited so that a backtick inside it stays text. */
    fun span(text: String): String {
        val longestRun = BACKTICKS.findAll(text).maxOfOrNull { it.value.length } ?: 0
        val delimiter = "`".repeat(longestRun + 1)
        val padding = if (text.startsWith('`') || text.endsWith('`')) " " else ""
        return "$delimiter$padding$text$padding$delimiter"
    }

    private val BACKTICKS = Regex("`+")
}
