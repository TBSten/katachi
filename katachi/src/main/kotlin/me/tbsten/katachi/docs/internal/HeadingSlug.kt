package me.tbsten.katachi.docs.internal

private val HEADING_LINE: Regex = Regex("^ {0,3}#{1,6}[ \t]+(.+?)[ \t]*#*[ \t]*$")

private val FENCE_OPEN: Regex = Regex("^ {0,3}(`{3,}|~{3,})")

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

// Must match the heading ids the docs site (github-slugger rule) generates.
internal class HeadingSlugger {
    private val occurrences = HashMap<String, Int>()

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
