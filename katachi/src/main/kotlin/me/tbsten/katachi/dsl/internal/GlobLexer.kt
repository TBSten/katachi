package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.KatachiGlobSyntaxException

// Star and DoubleStar are the only tokens whose matched text is handed back as a value.
internal sealed interface GlobToken {
    val start: Int
    val end: Int

    class Literal(val character: Char, override val start: Int, override val end: Int) : GlobToken

    class Star(val capturing: Boolean, override val start: Int, override val end: Int) : GlobToken

    class DoubleStar(override val start: Int, override val end: Int) : GlobToken

    class Question(override val start: Int, override val end: Int) : GlobToken

    class CharClass(
        val negated: Boolean,
        val members: List<GlobClassMember>,
        override val start: Int,
        override val end: Int,
    ) : GlobToken

    class Alternation(
        val alternatives: List<List<GlobToken>>,
        override val start: Int,
        override val end: Int,
    ) : GlobToken

    class Separator(override val start: Int, override val end: Int) : GlobToken
}

internal sealed interface GlobClassMember {
    class Single(val character: Char) : GlobClassMember
    class Range(val from: Char, val to: Char) : GlobClassMember
}

// Lenient reads never throw: an unclosed `{` or `[` runs to the end of the text, and an escape that escapes
// nothing is kept as written.
internal object GlobLexer {
    private const val MAX_EXPANSION: Int = 64

    fun scan(text: String, separator: Char): List<GlobToken> = Scanner(text, separator, strict = true).run()

    fun scanLeniently(text: String, separator: Char? = null): List<GlobToken> =
        Scanner(text, separator, strict = false).run()

    fun capturingStarCount(segment: String): Int =
        scanLeniently(segment).count { it is GlobToken.Star && it.capturing }

    fun hasGlobWildcard(text: String): Boolean = scanLeniently(text).any { it.isWildcard() }

    fun hasNonCapturingWildcard(text: String): Boolean =
        scanLeniently(text).any { it is GlobToken.Question || it is GlobToken.CharClass || it is GlobToken.Alternation }

    fun replaceCapturingStars(segment: String, replacement: (index: Int) -> String?): String {
        val result = StringBuilder()
        var copiedUntil = 0
        var index = 0
        for (token in scanLeniently(segment)) {
            if (token !is GlobToken.Star || !token.capturing) continue
            val replaced = replacement(index++) ?: continue
            result.append(segment, copiedUntil, token.start).append(replaced)
            copiedUntil = token.end
        }
        return result.append(segment, copiedUntil, segment.length).toString()
    }

    // null when expansion would produce more than 64 strings.
    fun expandBraces(pattern: String): List<String>? {
        var results = listOf("")
        var copiedUntil = 0
        for (token in scanLeniently(pattern)) {
            if (token !is GlobToken.Alternation) continue
            val literal = pattern.substring(copiedUntil, token.start)
            val alternatives = token.alternatives.map { alternative ->
                if (alternative.isEmpty()) "" else pattern.substring(alternative.first().start, alternative.last().end)
            }
            if (results.size.toLong() * alternatives.size > MAX_EXPANSION) return null
            results = results.flatMap { prefix -> alternatives.map { prefix + literal + it } }
            copiedUntil = token.end
        }
        val tail = pattern.substring(copiedUntil)
        return results.map { it + tail }
    }

    fun unescapeLiteral(text: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < text.length) {
            if (text[index] == '\\' && index + 1 < text.length) index++
            result.append(text[index])
            index++
        }
        return result.toString()
    }

    private fun GlobToken.isWildcard(): Boolean = this !is GlobToken.Literal && this !is GlobToken.Separator

    private class Scanner(
        private val text: String,
        private val separator: Char?,
        private val strict: Boolean,
    ) {
        private var position = 0

        fun run(): List<GlobToken> {
            val tokens = mutableListOf<GlobToken>()
            while (position < text.length) {
                val start = position
                when (val character = text[position]) {
                    '\\' -> tokens += GlobToken.Literal(readEscape(), start, position)
                    '*' -> tokens += readTopLevelStar()
                    '?' -> tokens += GlobToken.Question(start, ++position)
                    '[' -> tokens += readClass()
                    '{' -> tokens += readBraces()
                    '}', ']' -> {
                        if (strict) throw problem(GlobProblem.UnmatchedClosing(character))
                        tokens += GlobToken.Literal(character, start, ++position)
                    }
                    separator -> tokens += GlobToken.Separator(start, ++position)
                    else -> tokens += GlobToken.Literal(character, start, ++position)
                }
            }
            return tokens
        }

        private fun problem(problem: GlobProblem) = KatachiGlobSyntaxException(text, problem)

        private fun segmentAround(index: Int): String {
            val from = separator?.let { text.lastIndexOf(it, index - 1) + 1 } ?: 0
            val to = separator?.let { text.indexOf(it, index).takeIf { found -> found >= 0 } } ?: text.length
            return text.substring(from, to)
        }

        private fun readEscape(): Char {
            val escaped = text.getOrNull(position + 1)
            if (escaped == null) {
                if (strict) throw problem(GlobProblem.TrailingBackslash)
                return text[position++]
            }
            if (strict && escaped !in GLOB_ESCAPABLE) throw problem(GlobProblem.UnescapableCharacter(escaped))
            position += 2
            return escaped
        }

        private fun readTopLevelStar(): GlobToken {
            val start = position
            if (strict && text.getOrNull(position + 1) == '*') {
                val segment = segmentAround(position)
                if (segment != "**") throw problem(GlobProblem.DoubleStarInsideSegment(segment))
                position += 2
                return GlobToken.DoubleStar(start, position)
            }
            return GlobToken.Star(capturing = true, start, ++position)
        }

        private fun readClass(): GlobToken {
            val start = position
            position++
            val negated = text.getOrNull(position).let { it == '!' || it == '^' }
            if (negated) position++
            if (strict && text.getOrNull(position) == ']') throw problem(GlobProblem.EmptyCharacterClass)
            val members = mutableListOf<GlobClassMember>()
            while (true) {
                if (position >= text.length) {
                    if (strict) throw problem(GlobProblem.UnclosedBracket(segmentAround(start)))
                    break
                }
                if (text[position] == ']') {
                    position++
                    break
                }
                val from = readClassCharacter()
                val isRange = text.getOrNull(position) == '-' && position + 1 < text.length && text[position + 1] != ']'
                if (!isRange) {
                    members += GlobClassMember.Single(from)
                    continue
                }
                position++
                val to = readClassCharacter()
                if (strict && from > to) throw problem(GlobProblem.ReversedRange(from, to))
                members += GlobClassMember.Range(from, to)
            }
            return GlobToken.CharClass(negated, members, start, position)
        }

        private fun readClassCharacter(): Char {
            val character = text[position]
            if (character == '\\') return readEscape()
            if (strict && character == separator) throw problem(GlobProblem.SeparatorInBrackets(character))
            position++
            return character
        }

        private fun readBraces(): GlobToken {
            val start = position
            position++
            val alternatives = mutableListOf<List<GlobToken>>()
            var current = mutableListOf<GlobToken>()
            while (true) {
                if (position >= text.length) {
                    if (strict) throw problem(GlobProblem.UnclosedBrace(segmentAround(start)))
                    alternatives += current
                    break
                }
                val tokenStart = position
                when (val character = text[position]) {
                    '}' -> {
                        position++
                        if (strict && alternatives.isEmpty() && current.isEmpty()) {
                            throw problem(GlobProblem.EmptyBraces)
                        }
                        alternatives += current
                        break
                    }
                    ',' -> {
                        alternatives += current
                        current = mutableListOf()
                        position++
                    }
                    '\\' -> current += GlobToken.Literal(readEscape(), tokenStart, position)
                    '{' -> {
                        if (strict) throw problem(GlobProblem.NestedBraces)
                        current += GlobToken.Literal(character, tokenStart, ++position)
                    }
                    '*' -> {
                        if (strict && text.getOrNull(position + 1) == '*') throw problem(GlobProblem.DoubleStarInBraces)
                        current += GlobToken.Star(capturing = false, tokenStart, ++position)
                    }
                    '?' -> current += GlobToken.Question(tokenStart, ++position)
                    '[' -> current += readClass()
                    ']' -> {
                        if (strict) throw problem(GlobProblem.UnmatchedClosing(character))
                        current += GlobToken.Literal(character, tokenStart, ++position)
                    }
                    separator -> {
                        if (strict) throw problem(GlobProblem.SeparatorInBraces(character))
                        current += GlobToken.Literal(character, tokenStart, ++position)
                    }
                    else -> current += GlobToken.Literal(character, tokenStart, ++position)
                }
            }
            return GlobToken.Alternation(alternatives, start, position)
        }
    }
}
