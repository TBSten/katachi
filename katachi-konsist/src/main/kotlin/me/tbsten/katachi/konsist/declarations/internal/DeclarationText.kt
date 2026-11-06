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

// `context(logger: Logger, _: Clock)` in front of the declaration's keyword; a context receiver (`context(Logger)`) is named `_`.
// The one place context parameters are read: Konsist 1.0 has `contextParameters`, which can take its place.
internal fun contextParametersOf(header: String): List<ParameterFact> {
    val code = codeOnly(header)
    val keyword = DECLARATION_KEYWORD.find(code)?.range?.first ?: code.length
    val context = CONTEXT_START.find(code)?.takeIf { it.range.first < keyword } ?: return emptyList()
    val open = context.range.last
    val close = closingParenthesisOf(code, open) ?: return emptyList()
    val inside = code.substring(open + 1, close)
    val topLevel = topLevelOf(inside)
    val entries = mutableListOf<IntRange>()
    var from = 0
    for (index in inside.indices) {
        if (inside[index] == ',' && topLevel[index]) {
            entries += from until index
            from = index + 1
        }
    }
    entries += from until inside.length
    return entries.mapNotNull { range ->
        val entry = header.substring(open + 1 + range.first, open + 1 + range.last + 1).trim()
        if (entry.isEmpty()) return@mapNotNull null
        val entryCode = codeOnly(entry)
        val entryTop = topLevelOf(entryCode)
        val colon = entryCode.indices.firstOrNull { entryCode[it] == ':' && entryTop[it] }
        val name = if (colon == null) "_" else entry.substring(0, colon).trim().removeSurrounding("`")
        val type = if (colon == null) entry else entry.substring(colon + 1).trim()
        ParameterFact(name, typeTextOf(type))
    }
}

private val CONTEXT_START = Regex("""\bcontext\s*\(""")

private val DECLARATION_KEYWORD = Regex("""\b(?:fun|val|var|class|interface|object|typealias|constructor)\b""")

private fun closingParenthesisOf(code: String, open: Int): Int? {
    var depth = 0
    for (index in open until code.length) {
        when (code[index]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) return index
            }
        }
    }
    return null
}

// An explicit backing field (`field = ...` or `field: Type = ...` on a line of its own under the property): null without one,
// the type as written after its `:`, or an empty text when it writes none.
internal fun explicitBackingFieldOf(text: String): String? {
    val code = codeOnly(text)
    var depth = 0
    var lineStart = true
    for ((index, char) in code.withIndex()) {
        when {
            char == '(' || char == '{' || char == '[' -> depth++
            char == ')' || char == '}' || char == ']' -> depth = maxOf(0, depth - 1)
            char == '\n' -> {
                lineStart = true
                continue
            }
            char.isWhitespace() -> continue
            depth == 0 && lineStart && keywordAt(code, index, "field") -> {
                val rest = code.substring(index + "field".length).trimStart(' ', '\t')
                val start = code.length - rest.length
                when (rest.firstOrNull()) {
                    '=' -> return ""
                    ':' -> {
                        val typeCode = code.substring(start + 1)
                        val top = topLevelOf(typeCode)
                        val end = typeCode.indices.firstOrNull { (typeCode[it] == '=' || typeCode[it] == '\n') && top[it] } ?: typeCode.length
                        return text.substring(start + 1, start + 1 + end).trim()
                    }
                }
            }
        }
        lineStart = false
    }
    return null
}
