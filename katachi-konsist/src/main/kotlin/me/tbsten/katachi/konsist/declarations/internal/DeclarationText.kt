package me.tbsten.katachi.konsist.declarations.internal

// Konsist returns the receiver as an extension property's type, so the type is read off the text.

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
