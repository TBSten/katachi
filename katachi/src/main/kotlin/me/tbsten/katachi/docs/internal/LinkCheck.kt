package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.docs.BrokenDocumentLink
import me.tbsten.katachi.docs.KatachiBrokenDocumentLinkException

// A relative link a user wrote in a `description` is checked like any other, and stays that way.
// Reading the finished pages cannot tell that paragraph from one katachi wrote, and such a link
// resolves against an output tree whose depth katachi picked -- so there is nobody else who
// could check it. It is the one case where the failure is not katachi's bug, which is why
// KatachiBrokenDocumentLinkException's message carries a sentence about it. Recording links as
// they are written would separate the two, at the cost of a second channel between every writer
// and this check; that is more machinery than one extra sentence is worth.
internal fun checkDocumentLinks(pages: Map<String, String>) {
    val broken = pages.flatMap { (source, content) ->
        linksIn(content)
            .filter { isRelative(it.target) }
            .mapNotNull { link ->
                val resolved = resolveAgainst(source, link.target)
                if (resolved != null && resolved in pages) {
                    null
                } else {
                    BrokenDocumentLink(source = source, text = link.text, target = link.target, resolved = resolved)
                }
            }
    }
    if (broken.isNotEmpty()) throw KatachiBrokenDocumentLinkException(broken)
}

private class DocumentLink(val text: String, val target: String)

private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.\\-]*:")

private fun isRelative(target: String): Boolean = target.isNotEmpty() &&
    !target.startsWith("#") &&
    !target.startsWith("/") &&
    !SCHEME.containsMatchIn(target)

private fun resolveAgainst(source: String, target: String): String? {
    val segments = source.split('/').dropLast(1).toMutableList()
    for (segment in target.substringBefore('#').split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.size - 1)
            else -> segments += segment
        }
    }
    return segments.joinToString("/")
}

private fun linksIn(content: String): List<DocumentLink> {
    val links = mutableListOf<DocumentLink>()
    var fence: String? = null
    for (line in content.lines()) {
        val marker = fenceMarkerOf(line.trimStart())
        if (fence != null) {
            if (marker != null && marker.startsWith(fence)) fence = null
            continue
        }
        if (marker != null) {
            fence = marker
            continue
        }
        appendLinksOf(withoutCodeSpans(line), links)
    }
    return links
}

private fun fenceMarkerOf(trimmed: String): String? {
    val char = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
    return trimmed.takeWhile { it == char }.takeIf { it.length >= FENCE_LENGTH }
}

private const val FENCE_LENGTH: Int = 3

private fun withoutCodeSpans(line: String): String {
    if ('`' !in line) return line
    val blanked = StringBuilder()
    var cursor = 0
    while (cursor < line.length) {
        if (line[cursor] != '`') {
            blanked.append(line[cursor])
            cursor++
            continue
        }
        val ticks = line.drop(cursor).takeWhile { it == '`' }
        val close = line.indexOf(ticks, startIndex = cursor + ticks.length)
        if (close < 0) {
            blanked.append(ticks)
            cursor += ticks.length
        } else {
            repeat(close + ticks.length - cursor) { blanked.append(' ') }
            cursor = close + ticks.length
        }
    }
    return blanked.toString()
}

private fun appendLinksOf(line: String, links: MutableList<DocumentLink>) {
    var cursor = 0
    while (cursor < line.length) {
        val open = line.indexOf('[', cursor)
        if (open < 0) return
        val close = closingBracketOf(line, open)
        val target = if (close > 0 && line.getOrNull(close + 1) == '(') line.indexOf(')', close + 2) else -1
        if (target < 0) {
            cursor = open + 1
            continue
        }
        links += DocumentLink(
            text = line.substring(open + 1, close).replace("\\]", "]"),
            target = line.substring(close + 2, target).trim(),
        )
        cursor = target + 1
    }
}

private fun closingBracketOf(line: String, open: Int): Int {
    var cursor = open + 1
    while (cursor < line.length) {
        when {
            line[cursor] == '\\' -> cursor++
            line[cursor] == ']' -> return cursor
        }
        cursor++
    }
    return -1
}
