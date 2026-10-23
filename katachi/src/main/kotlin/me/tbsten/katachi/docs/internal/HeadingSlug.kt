package me.tbsten.katachi.docs.internal

private val HEADING_LINE: Regex = Regex("^ {0,3}#{1,6}[ \t]+(.+?)[ \t]*#*[ \t]*$")

private val FENCE_OPEN: Regex = Regex("^ {0,3}(`{3,}|~{3,})")

/**
 * The titles of the ATX headings (`## Title`) of [markdown], in order.
 *
 * Headings inside a fenced code block are not headings. A fence is closed by a line of the same
 * marker, at least as long as the opening one, with nothing else on it, so a line such as
 * ```` ```kotlin ```` inside a fence does not close it. Known limits: a Setext heading (an
 * underline of `===`) is not read, and a title is taken as written, so inline markup in it is not
 * reduced to its text the way the rendered page's id is (`_x_` keeps its underscores, a link
 * `[text](url)` keeps its url).
 */
internal fun headingTitles(markdown: String): List<String> {
    val titles = ArrayList<String>()
    var fence: String? = null
    for (line in markdown.lineSequence()) {
        val open = fence
        if (open != null) {
            val trimmed = line.trim()
            if (trimmed.length >= open.length && trimmed.all { it == open[0] }) fence = null
            continue
        }
        val marker = FENCE_OPEN.find(line)?.groupValues?.get(1)
        if (marker != null) {
            fence = marker
            continue
        }
        HEADING_LINE.matchEntire(line)?.let { titles += it.groupValues[1] }
    }
    return titles
}

/**
 * Hands out the ids the docs site gives the headings of one page, in the order they are met.
 *
 * The rule is the one GitHub and Starlight share (`github-slugger`): lower case, spaces to `-`,
 * and every character that is not a letter, a digit, `-` or `_` dropped, so
 * `## Kotlin file content` is `kotlin-file-content`. A heading whose id was already given on the
 * page gets a numeric suffix (`-1`, `-2`, ...), which is why the links of a page have to be worked
 * out by one slugger fed with every heading of the page, in page order.
 */
internal class HeadingSlugger {
    private val occurrences = HashMap<String, Int>()

    /** The id of the next heading, titled [title], of the page. */
    fun next(title: String): String {
        val original = slugOf(title)
        var result = original
        while (result in occurrences) {
            val count = (occurrences[original] ?: 0) + 1
            occurrences[original] = count
            result = "$original-$count"
        }
        occurrences[result] = 0
        return result
    }
}

private fun slugOf(title: String): String = buildString {
    for (char in title.lowercase()) {
        when {
            char == ' ' -> append('-')
            char == '-' || char == '_' || char.isLetterOrDigit() -> append(char)
        }
    }
}
