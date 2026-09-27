package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.KatachiInvalidBaselineFileException
import me.tbsten.katachi.check.KatachiInvalidBaselineFileException.Problem

/**
 * How deep arrays and objects may nest. A baseline file nests three levels; the limit is there
 * so that a broken file fails with a message instead of a [StackOverflowError].
 */
private const val MAX_DEPTH: Int = 64

/** The byte order mark an editor may put at the start of a file. It says nothing about content. */
private const val BYTE_ORDER_MARK: Char = '\uFEFF'

/** A number as RFC 8259 writes one, in ASCII digits only. */
private val JSON_NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

/** A parsed JSON value and the line it starts on. */
internal sealed interface JsonNode {
    val line: Int

    /** An object, its members in the order the file lists them; a name appears at most once. */
    class Obj(override val line: Int, val members: List<Pair<String, JsonNode>>) : JsonNode
    class Arr(override val line: Int, val items: List<JsonNode>) : JsonNode
    class Str(override val line: Int, val value: String) : JsonNode
    class Num(override val line: Int, val text: String) : JsonNode
    class Bool(override val line: Int, val value: Boolean) : JsonNode
    class Null(override val line: Int) : JsonNode
}

/** The complaint about line [line] of [file]. */
internal fun invalidBaseline(file: String, line: Int, problem: Problem, detail: String): KatachiInvalidBaselineFileException =
    KatachiInvalidBaselineFileException(file, line, problem, detail)

/**
 * The first merge conflict marker in [text] as its line and the marker, or `null` when there is
 * none. Looked for before the JSON is, because a marker breaks the JSON somewhere else entirely,
 * and "not valid JSON at line 12" would hide what happened.
 */
internal fun mergeConflictMarkerIn(text: String): Pair<Int, String>? {
    text.lineSequence().forEachIndexed { index, line ->
        for (marker in listOf("<<<<<<<", "=======", ">>>>>>>")) {
            val rest = line.removeSuffix("\r")
            if (rest.startsWith(marker) && (rest.length == marker.length || rest[marker.length] == ' ')) {
                return index + 1 to marker
            }
        }
    }
    return null
}

/**
 * A strict JSON reader (RFC 8259) that keeps track of the line it is on: one object never has a
 * name twice, and a number is written in ASCII digits.
 */
internal class JsonReader(private val text: String, private val file: String) {
    private var position = 0
    private var line = 1

    fun readDocument(): JsonNode {
        if (text.startsWith(BYTE_ORDER_MARK)) position = 1
        val value = readValue(depth = 0)
        skipWhitespace()
        if (position < text.length) fail("unexpected text after the end of the JSON value")
        return value
    }

    private fun readValue(depth: Int): JsonNode {
        skipWhitespace()
        if (position >= text.length) fail("the file ends before the JSON value does")
        val start = line
        return when (val char = text[position]) {
            '{' -> readObject(start, depth + 1)
            '[' -> readArray(start, depth + 1)
            '"' -> JsonNode.Str(start, readString())
            't' -> literal("true", JsonNode.Bool(start, true))
            'f' -> literal("false", JsonNode.Bool(start, false))
            'n' -> literal("null", JsonNode.Null(start))
            else -> if (char == '-' || char in '0'..'9') JsonNode.Num(start, readNumber()) else fail("unexpected '$char'")
        }
    }

    private fun readObject(start: Int, depth: Int): JsonNode.Obj {
        if (depth > MAX_DEPTH) fail("arrays and objects are nested more than $MAX_DEPTH deep")
        position++
        val members = mutableListOf<Pair<String, JsonNode>>()
        val names = HashSet<String>()
        skipWhitespace()
        if (peek() == '}') {
            position++
            return JsonNode.Obj(start, members)
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("expected a quoted name")
            val nameLine = line
            val name = readString()
            if (!names.add(name)) throw invalidBaseline(file, nameLine, Problem.DuplicateField, name)
            skipWhitespace()
            expect(':')
            members += name to readValue(depth)
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                '}' -> {
                    position++
                    return JsonNode.Obj(start, members)
                }
                else -> fail("expected ',' or '}'")
            }
        }
    }

    private fun readArray(start: Int, depth: Int): JsonNode.Arr {
        if (depth > MAX_DEPTH) fail("arrays and objects are nested more than $MAX_DEPTH deep")
        position++
        val items = mutableListOf<JsonNode>()
        skipWhitespace()
        if (peek() == ']') {
            position++
            return JsonNode.Arr(start, items)
        }
        while (true) {
            items += readValue(depth)
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                ']' -> {
                    position++
                    return JsonNode.Arr(start, items)
                }
                else -> fail("expected ',' or ']'")
            }
        }
    }

    private fun readString(): String {
        position++
        val out = StringBuilder()
        while (true) {
            if (position >= text.length) fail("a string is not closed")
            val char = text[position++]
            when {
                char == '"' -> return out.toString()
                char == '\\' -> out.append(readEscape())
                char < ' ' -> fail("a control character inside a string")
                else -> out.append(char)
            }
        }
    }

    private fun readEscape(): Char {
        if (position >= text.length) fail("a string is not closed")
        return when (val escaped = text[position++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                val hex = text.substring(position, minOf(position + 4, text.length))
                val isHex = hex.length == 4 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                if (!isHex) fail("a broken \\u escape")
                position += 4
                hex.toInt(16).toChar()
            }
            else -> fail("an unknown escape '\\$escaped'")
        }
    }

    private fun readNumber(): String {
        val from = position
        while (position < text.length && (text[position] in '0'..'9' || text[position] in ".eE+-")) position++
        val number = text.substring(from, position)
        if (!JSON_NUMBER.matches(number)) fail("a malformed number \"$number\"")
        return number
    }

    private fun literal(word: String, node: JsonNode): JsonNode {
        if (!text.startsWith(word, position)) fail("unexpected '${text[position]}'")
        position += word.length
        return node
    }

    private fun expect(char: Char) {
        if (peek() != char) fail("expected '$char'")
        position++
    }

    private fun peek(): Char? = text.getOrNull(position)

    private fun skipWhitespace() {
        while (position < text.length) {
            when (text[position]) {
                '\n' -> line++
                ' ', '\t', '\r' -> Unit
                else -> return
            }
            position++
        }
    }

    private fun fail(what: String): Nothing = throw invalidBaseline(file, line, Problem.NotJson, what)
}
