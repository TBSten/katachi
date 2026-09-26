package me.tbsten.katachi.intellij.data.json

import java.nio.file.Path

/**
 * Parses [text] as one JSON document (RFC 8259). A leading BOM is skipped: katachi writes none, but
 * an editor saving the file by hand might.
 *
 * @throws KatachiMalformedTemplateJsonException when [text] is empty, cut off or not JSON.
 */
internal fun parseJsonText(text: String, file: Path): JsonValue = JsonTextParser(text, file).parseDocument()

private class JsonTextParser(private val text: String, private val file: Path) {
    private var pos = if (text.startsWith('﻿')) 1 else 0

    fun parseDocument(): JsonValue {
        skipWhitespace()
        if (pos >= text.length) fail("the file is empty")
        val value = parseValue()
        skipWhitespace()
        if (pos < text.length) fail("unexpected '${text[pos]}' after the document")
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        if (pos >= text.length) fail("the document ends in the middle")
        return when (val c = text[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.JsonString(parseString())
            't' -> literal("true", JsonValue.JsonBoolean(true))
            'f' -> literal("false", JsonValue.JsonBoolean(false))
            'n' -> literal("null", JsonValue.JsonNull)
            else -> if (c == '-' || c.isAsciiDigit()) parseNumber() else fail("unexpected '$c'")
        }
    }

    private fun parseObject(): JsonValue.JsonObject {
        pos++ // {
        val members = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (peek() == '}') {
            pos++
            return JsonValue.JsonObject(members)
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("expected a key")
            val key = parseString()
            skipWhitespace()
            expect(':')
            // A duplicated key keeps the first value, like the list keeps the first role name.
            members.putIfAbsent(key, parseValue())
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return JsonValue.JsonObject(members)
                }
                else -> fail("expected ',' or '}'")
            }
        }
    }

    private fun parseArray(): JsonValue.JsonArray {
        pos++ // [
        val elements = mutableListOf<JsonValue>()
        skipWhitespace()
        if (peek() == ']') {
            pos++
            return JsonValue.JsonArray(elements)
        }
        while (true) {
            elements += parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return JsonValue.JsonArray(elements)
                }
                else -> fail("expected ',' or ']'")
            }
        }
    }

    private fun parseString(): String {
        pos++ // "
        val out = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("a string is not closed")
            when (val c = text[pos++]) {
                '"' -> return out.toString()
                '\\' -> out.append(parseEscape())
                else -> if (c < ' ') fail("a raw control character in a string") else out.append(c)
            }
        }
    }

    private fun parseEscape(): Char {
        if (pos >= text.length) fail("a string is not closed")
        return when (val c = text[pos++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (pos + 4 > text.length) fail("a \\u escape is cut off")
                val hex = text.substring(pos, pos + 4)
                pos += 4
                hex.toIntOrNull(16)?.toChar() ?: fail("a \\u escape is not hexadecimal")
            }
            else -> fail("an unknown escape '\\$c'")
        }
    }

    private fun parseNumber(): JsonValue.JsonNumber {
        val start = pos
        if (peek() == '-') pos++
        while (pos < text.length && (text[pos].isAsciiDigit() || text[pos] in ".eE+-")) pos++
        val number = text.substring(start, pos)
        if (number == "-" || number.toDoubleOrNull() == null) fail("'$number' is not a number")
        return JsonValue.JsonNumber(number)
    }

    private fun literal(word: String, value: JsonValue): JsonValue {
        if (!text.startsWith(word, pos)) fail("unexpected '${text[pos]}'")
        pos += word.length
        return value
    }

    private fun expect(c: Char) {
        if (peek() != c) fail("expected '$c'")
        pos++
    }

    private fun peek(): Char? = text.getOrNull(pos)

    private fun skipWhitespace() {
        while (pos < text.length && text[pos] in " \t\r\n") pos++
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    private fun fail(reason: String): Nothing {
        val problem = if (pos >= text.length && reason != "the file is empty") "$reason (the file ends at offset $pos)" else "$reason at offset $pos"
        throw KatachiMalformedTemplateJsonException(file = file, problem = problem)
    }
}
