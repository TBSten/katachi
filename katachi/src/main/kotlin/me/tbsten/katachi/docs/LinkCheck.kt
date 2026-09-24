package me.tbsten.katachi.docs

/**
 * Every relative link of the generated documentation, resolved against the pages that were
 * generated.
 *
 * The last step of building the documentation, and the reason the rest of it can stay simple. A
 * link between two pages is written from where one page sits to where another one does, and the
 * depth of both follows the nesting of the definition -- so the one mistake this layer can make
 * silently is a link that points nowhere. Generation still succeeds, the page still renders, and
 * the reader finds out by clicking.
 *
 * Checking it costs nothing here because the generator holds both halves of the answer: the
 * links it wrote, and the exact set of pages it produced. No file is read and no URL is fetched
 * -- an external link is somebody else's uptime, not katachi's.
 */
// TODO(v0.2 ステップ6): confirm that a role's `description` stays in scope.
//  Reading the finished pages cannot tell a paragraph a user wrote from one katachi wrote, so a
//  relative link in a `description` is checked like any other. That is arguably right -- such a
//  link resolves against an output tree whose depth katachi picked, so nobody else can check it
//  -- but it is the one case where the failure is not katachi's bug, and the message has to say
//  so in an extra sentence. Recording links as they are written would separate the two at the
//  cost of a second channel between the writers and this check.
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

/** One inline link as it was written, before anything is decided about where it points. */
private class DocumentLink(val text: String, val target: String)

/** What a link has to start with to be somebody else's: `https:`, `mailto:`, and the like. */
private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.\\-]*:")

/**
 * Whether [target] is a path between two generated pages, rather than something to leave alone.
 *
 * Four things are left alone: a link with a scheme, a protocol relative `//host/...`, a path
 * from the site root (there is no site here to be the root of), and a bare `#anchor`, which
 * names a heading of the page it is written on. Headings are not checked at all -- the generated
 * ones come from a user's `title`, so they are not katachi's to promise.
 */
private fun isRelative(target: String): Boolean = target.isNotEmpty() &&
    !target.startsWith("#") &&
    !target.startsWith("/") &&
    !SCHEME.containsMatchIn(target)

/**
 * Where [target] lands when it is followed from [source], or `null` when it leaves the output
 * root.
 *
 * Nothing about `./` or `../` is assumed, because nothing can be: a group nests as deep as the
 * definition nests it, so the number of steps between two pages is a property of the two pages
 * and not of the code that writes the link.
 */
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

/**
 * The inline links of one page, skipping everything that is not prose.
 *
 * A fenced block is skipped whole: the placement tree lives in one, and its paths are full of
 * the characters a link is made of. Code spans are blanked out for the same reason one line
 * lower down -- a role's `## 例` writes identifiers in backticks, and a `description` may write
 * anything at all in them.
 */
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

/** The run of backticks or tildes a fence line opens or closes with, or `null` for prose. */
private fun fenceMarkerOf(trimmed: String): String? {
    val char = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
    return trimmed.takeWhile { it == char }.takeIf { it.length >= FENCE_LENGTH }
}

/** How many backticks or tildes open a fenced block. */
private const val FENCE_LENGTH: Int = 3

/**
 * [line] with every code span replaced by spaces of the same width.
 *
 * The width is kept so that nothing said about a column later means something else, and because
 * a link split across a code span is not a link either way.
 */
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

/** The `[text](target)` occurrences of one line of prose. An image is one of them, on purpose. */
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

/** Where the link text opened at [open] ends, stepping over the `\]` katachi escapes it with. */
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
