package me.tbsten.katachi.konsist.declarations.internal

/*
 * Kotlin source read as text, for the few things Konsist's parse does not give: the supertypes of
 * a header, the type of an extension property, and what is written before a `context(...)`.
 *
 * Every reader here works on `codeOnly(text)`, where comments and the insides of string and
 * character literals are blanked out, so that a `:`, a `,` or a bracket inside one is never taken
 * for code. Blanking keeps every offset, so a range found in the code is the same range of the text.
 */

/**
 * [text] with every comment, and what every string and character literal holds, made spaces. A
 * line break stays a line break, and the quotes stay: `"a(b" // c` reads `"   "     `.
 *
 * Block comments nest, as in Kotlin. A template's expression (`${...}`) is blanked with its string.
 */
internal fun codeOnly(text: String): String {
    val out = StringBuilder(text)
    CodeBlanker(text, out).code(0, inTemplate = false)
    return out.toString()
}

/** One pass over a text, writing its blanked form into [out] (a copy of the text). */
private class CodeBlanker(private val text: String, private val out: StringBuilder) {
    /**
     * Code from [start], up to the end of the text, or -- [inTemplate] -- up to and with the `}`
     * that closes a template's `${`. Returns the index after what was read.
     */
    fun code(start: Int, inTemplate: Boolean): Int {
        var index = start
        var braces = 0
        while (index < text.length) {
            val char = text[index]
            index = when {
                text.startsWith("//", index) -> lineCommentEnd(index).also { blank(index, it) }
                text.startsWith("/*", index) -> blockCommentEnd(index).also { blank(index, it) }
                text.startsWith(RAW_QUOTES, index) -> rawStringEnd(index)
                char == '"' -> stringEnd(index)
                char == '\'' -> charLiteralEnd(index)
                char == '`' -> text.indexOf('`', index + 1).let { if (it < 0) text.length else it + 1 }
                char == '{' -> {
                    braces++
                    index + 1
                }
                char == '}' && inTemplate && braces == 0 -> return index + 1
                char == '}' -> {
                    braces--
                    index + 1
                }
                else -> index + 1
            }
        }
        return index
    }

    private fun lineCommentEnd(start: Int): Int = text.indexOf('\n', start).let { if (it < 0) text.length else it }

    private fun blockCommentEnd(start: Int): Int {
        var depth = 0
        var index = start
        while (index < text.length) {
            when {
                text.startsWith("/*", index) -> {
                    depth++
                    index += 2
                }
                text.startsWith("*/", index) -> {
                    depth--
                    index += 2
                    if (depth == 0) return index
                }
                else -> index++
            }
        }
        return index
    }

    private fun stringEnd(start: Int): Int {
        var index = start + 1
        while (index < text.length) {
            val char = text[index]
            index = when {
                char == '"' -> return index + 1
                // An unterminated string ends at its line, as the Kotlin lexer ends it.
                char == '\n' -> return index
                char == '\\' -> (index + 2).also { blank(index, it) }
                text.startsWith("\${", index) -> template(index)
                else -> (index + 1).also { blank(index, it) }
            }
        }
        return index
    }

    private fun rawStringEnd(start: Int): Int {
        var index = start + RAW_QUOTES.length
        while (index < text.length) {
            index = when {
                text.startsWith(RAW_QUOTES, index) -> {
                    // The last three of a run of quotes close the string: `""""a""""` holds `"a"`.
                    var end = index + RAW_QUOTES.length
                    while (end < text.length && text[end] == '"') end++
                    blank(index, end - RAW_QUOTES.length)
                    return end
                }
                text.startsWith("\${", index) -> template(index)
                else -> (index + 1).also { blank(index, it) }
            }
        }
        return index
    }

    private fun charLiteralEnd(start: Int): Int {
        var index = start + 1
        while (index < text.length && text[index] != '\'' && text[index] != '\n') {
            index = if (text[index] == '\\') index + 2 else index + 1
        }
        blank(start + 1, index)
        return if (index < text.length && text[index] == '\'') index + 1 else index
    }

    /** The template whose `${` is at [start], blanked with what it holds. Returns the index after its `}`. */
    private fun template(start: Int): Int {
        val end = code(start + 2, inTemplate = true)
        blank(start, end)
        return end
    }

    /** Makes the characters in [from] until [until] spaces, keeping line breaks. */
    private fun blank(from: Int, until: Int) {
        for (index in from until minOf(until, text.length)) {
            if (text[index] != '\n') out.setCharAt(index, ' ')
        }
    }
}

private const val RAW_QUOTES: String = "\"\"\""

/**
 * For each index of [code], whether it is outside every `(...)` and `<...>`. A bracket itself is
 * never outside.
 *
 * Angle brackets are only counted outside parentheses: inside them is a constructor's parameter
 * list or a call's arguments, where `<` may be a comparison. A `>` closes only an open `<`, and
 * never as the end of `->`.
 */
internal fun topLevelOf(code: String): BooleanArray {
    val topLevel = BooleanArray(code.length)
    var parens = 0
    var angles = 0
    for ((index, char) in code.withIndex()) {
        when {
            char == '(' -> parens++
            char == ')' -> parens = maxOf(0, parens - 1)
            parens > 0 -> Unit
            char == '<' -> angles++
            char == '>' && angles > 0 && code.getOrNull(index - 1) != '-' -> angles--
            else -> topLevel[index] = angles == 0
        }
    }
    return topLevel
}

/** Whether the keyword [word] starts at [index] of [code], not as part of a longer name. */
internal fun keywordAt(code: String, index: Int, word: String): Boolean =
    code.startsWith(word, index) &&
        !isNameChar(code.getOrNull(index - 1)) &&
        !isNameChar(code.getOrNull(index + word.length))

/** A character a Kotlin name may hold. */
internal fun isNameChar(char: Char?): Boolean = char != null && (char.isLetterOrDigit() || char == '_')
