package me.tbsten.katachi.konsist.declarations.internal

/*
 * What Konsist 0.17.3 reads wrongly about a declaration, read off the text instead.
 *
 * - The type of an extension property: `KoPropertyDeclaration.type` returns the first type
 *   reference, which for `val String.size: Int` is the receiver `String`.
 * - What is written before a `context(...)`: its parser leaves `@Keep context(x: X)` out of the
 *   declaration, so its annotations are not read and its text (and line) start after it.
 */

/**
 * The type written on the extension property whose text is [header] (KDoc and annotations taken
 * off, as `headerTextOf` does) and whose name is [name], or `null` when none is written:
 * `val String.size: Int get() = length` is `Int`, `val String.size get() = length` is `null`.
 */
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

/**
 * The index right after `<receiver>.<name>` in [code], read from [from] (right after `val`), or
 * `null` when the name is not found. The name is the one no `.`, `?` or `<` follows, so a
 * receiver `Foo.Bar` of a property `Bar` is not taken for it.
 */
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

/** Whether the type written after a property's `:` ends at [index] of [rest]. */
private fun endsType(rest: String, index: Int): Boolean {
    val char = rest[index]
    return char == '=' || char == '\n' || char == ';' || char == '{' ||
        TYPE_ENDING_KEYWORDS.any { keywordAt(rest, index, it) && rest.getOrNull(index - 1)?.isWhitespace() == true }
}

/** What may follow a property's type on the same line: a delegate, an accessor, a type constraint. */
private val TYPE_ENDING_KEYWORDS: List<String> = listOf("by", "get", "set", "where")

/** The annotations written before a declaration's `context(...)`, and where what it starts with is. */
internal class WrittenBeforeContext(
    /** Each as written, `@Keep`, `@Deprecated("x")`, in order. */
    val annotationTexts: List<String>,
    /** The offset of the first annotation, or of `context` when there is none. */
    val start: Int,
)

/**
 * What is written before the `context(...)` that comes right before [declarationStart] in the file
 * [text], whose blanked form is [code], or `null` when no `context(...)` comes right before it.
 */
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

/** `@[A B]` as `@A` and `@B`; any other annotation as itself. */
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

/**
 * The offset of the `@` of the annotation that ends right before [end] (white space between),
 * or `null` when what ends there is not an annotation.
 */
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

/** What an annotation's name may hold besides a name's characters: `a.b.C`, `get:C`, `` `c d` ``. */
private const val ANNOTATION_NAME_CHARS: String = ".:`"

/** The index after the last character before [end] that is not white space. */
private fun spaceBefore(code: String, end: Int): Int {
    var index = end
    while (index > 0 && code[index - 1].isWhitespace()) index--
    return index
}

/** The index of the [open] that the [close] at [closeIndex] closes, or `null` when there is none. */
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
