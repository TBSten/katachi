package me.tbsten.katachi.konsist.declarations.internal

// Konsist reads these wrongly: an extension property's type comes back as the receiver, and `@Keep context(x: X)` is parsed without its annotations,
// with the text (and line) starting after it.

internal fun extensionPropertyTypeOf(header: String, name: String): String? {
    val code = codeOnly(header)
    val keyword = PROPERTY_KEYWORD.find(code) ?: return null
    val afterName = endOfReceiverAndName(code, keyword.range.last + 1, name) ?: return null
    var start = afterName
    while (start < code.length && (code[start] == ' ' || code[start] == '\t')) start++
    if (code.getOrNull(start) != ':') return null
    val rest = code.substring(start + 1)
    val topLevel = topLevelOf(rest)
    val end = rest.indices.firstOrNull { index -> topLevel[index] && endsType(rest, index) } ?: rest.length
    return header.substring(start + 1, start + 1 + end).trim().takeIf { it.isNotEmpty() }
}

private val PROPERTY_KEYWORD = Regex("""\b(?:val|var)\b""")

// The name is the one no `.`, `?` or `<` follows, so a receiver `Foo.Bar` of a property `Bar` is not taken for it.
private fun endOfReceiverAndName(code: String, from: Int, name: String): Int? {
    val topLevel = topLevelOf(code)
    for (written in listOf("`$name`", name)) {
        var index = code.indexOf(".$written", from)
        while (index >= 0) {
            val end = index + 1 + written.length
            val next = code.substring(end).trimStart(' ', '\t').firstOrNull()
            if (topLevel[index] && !isNameChar(code.getOrNull(end)) && next != '.' && next != '?' && next != '<') return end
            index = code.indexOf(".$written", index + 1)
        }
    }
    return null
}

private fun endsType(rest: String, index: Int): Boolean {
    val char = rest[index]
    return char == '=' || char == '\n' || char == ';' || char == '{' ||
        TYPE_ENDING_KEYWORDS.any { keywordAt(rest, index, it) && rest.getOrNull(index - 1)?.isWhitespace() == true }
}

private val TYPE_ENDING_KEYWORDS: List<String> = listOf("by", "get", "set", "where")

internal class WrittenBeforeContext(
    val annotationTexts: List<String>,
    val start: Int,
)

internal fun writtenBeforeContext(text: String, code: String, declarationStart: Int): WrittenBeforeContext? {
    val close = spaceBefore(code, declarationStart) - 1
    if (code.getOrNull(close) != ')') return null
    val open = openingBefore(code, close, '(', ')') ?: return null
    val keywordEnd = spaceBefore(code, open)
    val keywordStart = keywordEnd - CONTEXT.length
    if (keywordStart < 0 || !keywordAt(code, keywordStart, CONTEXT)) return null
    var start = keywordStart
    val annotations = ArrayDeque<String>()
    while (true) {
        val annotationStart = annotationBefore(code, start) ?: break
        val annotationEnd = spaceBefore(code, start)
        annotations.addFirst(text.substring(annotationStart, annotationEnd))
        start = annotationStart
    }
    return WrittenBeforeContext(annotations.flatMap(::splitAnnotationGroup), start)
}

private const val CONTEXT: String = "context"

private fun splitAnnotationGroup(annotation: String): List<String> {
    if (!annotation.startsWith("@[")) return listOf(annotation)
    val inner = annotation.removePrefix("@[").removeSuffix("]")
    val topLevel = topLevelOf(inner)
    val parts = mutableListOf<String>()
    var from = 0
    for (index in inner.indices) {
        if (inner[index].isWhitespace() && topLevel[index]) {
            parts += inner.substring(from, index)
            from = index + 1
        }
    }
    parts += inner.substring(from)
    return parts.filter { it.isNotBlank() }.map { "@" + it.trim() }
}

private fun annotationBefore(code: String, end: Int): Int? {
    var index = spaceBefore(code, end)
    when (code.getOrNull(index - 1)) {
        ']' -> {
            val open = openingBefore(code, index - 1, '[', ']') ?: return null
            return if (code.getOrNull(open - 1) == '@') open - 1 else null
        }
        ')' -> index = openingBefore(code, index - 1, '(', ')') ?: return null
    }
    if (code.getOrNull(index - 1) == '>') index = openingBefore(code, index - 1, '<', '>') ?: return null
    val nameEnd = index
    while (index > 0 && (isNameChar(code[index - 1]) || code[index - 1] in ANNOTATION_NAME_CHARS)) index--
    return if (index < nameEnd && code.getOrNull(index - 1) == '@') index - 1 else null
}

private const val ANNOTATION_NAME_CHARS: String = ".:`"

private fun spaceBefore(code: String, end: Int): Int {
    var index = end
    while (index > 0 && code[index - 1].isWhitespace()) index--
    return index
}

private fun openingBefore(code: String, closeIndex: Int, open: Char, close: Char): Int? {
    var depth = 0
    for (index in closeIndex downTo 0) {
        when (code[index]) {
            close -> depth++
            open -> {
                depth--
                if (depth == 0) return index
            }
        }
    }
    return null
}
