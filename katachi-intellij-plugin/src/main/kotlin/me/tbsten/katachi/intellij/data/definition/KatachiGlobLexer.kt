package me.tbsten.katachi.intellij.data.definition

internal sealed interface KatachiGlobToken {
    val start: Int
    val end: Int

    class Literal(val character: Char, override val start: Int, override val end: Int) : KatachiGlobToken

    class Star(val capturing: Boolean, override val start: Int, override val end: Int) : KatachiGlobToken

    class DoubleStar(override val start: Int, override val end: Int) : KatachiGlobToken

    class Question(override val start: Int, override val end: Int) : KatachiGlobToken

    class CharClass(
        val negated: Boolean,
        val members: List<KatachiGlobClassMember>,
        override val start: Int,
        override val end: Int,
    ) : KatachiGlobToken

    class Alternation(
        val alternatives: List<List<KatachiGlobToken>>,
        override val start: Int,
        override val end: Int,
    ) : KatachiGlobToken

    class Separator(override val start: Int, override val end: Int) : KatachiGlobToken
}

internal sealed interface KatachiGlobClassMember {
    class Single(val character: Char) : KatachiGlobClassMember
    class Range(val from: Char, val to: Char) : KatachiGlobClassMember
}

// Port of katachi's `GlobLexer`; `glob-cases.json` pins the two together.
// Reads leniently: only `KatachiGlob.compile` is strict.
internal object KatachiGlobLexer {
    private const val MAX_EXPANSION: Int = 64

    fun scan(text: String, separator: Char): List<KatachiGlobToken> = Scanner(text, separator, strict = true).run()

    fun scanLeniently(text: String, separator: Char? = null): List<KatachiGlobToken> =
        Scanner(text, separator, strict = false).run()

    fun capturingStarCount(segment: String): Int =
        scanLeniently(segment).count { it is KatachiGlobToken.Star && it.capturing }

    fun hasGlobWildcard(text: String): Boolean = scanLeniently(text).any { it.isWildcard() }

    fun hasNonCapturingWildcard(text: String): Boolean =
        scanLeniently(text).any { it is KatachiGlobToken.Question || it is KatachiGlobToken.CharClass || it is KatachiGlobToken.Alternation }

    fun replaceCapturingStars(segment: String, replacement: (index: Int) -> String?): String {
        val result = StringBuilder()
        var copiedUntil = 0
        var index = 0
        for (token in scanLeniently(segment)) {
            if (token !is KatachiGlobToken.Star || !token.capturing) continue
            val replaced = replacement(index++) ?: continue
            result.append(segment, copiedUntil, token.start).append(replaced)
            copiedUntil = token.end
        }
        return result.append(segment, copiedUntil, segment.length).toString()
    }

    fun expandBraces(pattern: String): List<String>? {
        var results = listOf("")
        var copiedUntil = 0
        for (token in scanLeniently(pattern)) {
            if (token !is KatachiGlobToken.Alternation) continue
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

    private fun KatachiGlobToken.isWildcard(): Boolean = this !is KatachiGlobToken.Literal && this !is KatachiGlobToken.Separator

    private class Scanner(
        private val text: String,
        private val separator: Char?,
        private val strict: Boolean,
    ) {
        private var position = 0

        fun run(): List<KatachiGlobToken> {
            val tokens = mutableListOf<KatachiGlobToken>()
            while (position < text.length) {
                val start = position
                when (val character = text[position]) {
                    '\\' -> tokens += KatachiGlobToken.Literal(readEscape(), start, position)
                    '*' -> tokens += readTopLevelStar()
                    '?' -> tokens += KatachiGlobToken.Question(start, ++position)
                    '[' -> tokens += readClass()
                    '{' -> tokens += readBraces()
                    '}', ']' -> {
                        if (strict) throw problem(KatachiGlobProblem.UnmatchedClosing(character))
                        tokens += KatachiGlobToken.Literal(character, start, ++position)
                    }
                    separator -> tokens += KatachiGlobToken.Separator(start, ++position)
                    else -> tokens += KatachiGlobToken.Literal(character, start, ++position)
                }
            }
            return tokens
        }

        private fun problem(problem: KatachiGlobProblem) = KatachiGlobSyntaxException(text, problem)

        private fun segmentAround(index: Int): String {
            val from = separator?.let { text.lastIndexOf(it, index - 1) + 1 } ?: 0
            val to = separator?.let { text.indexOf(it, index).takeIf { found -> found >= 0 } } ?: text.length
            return text.substring(from, to)
        }

        private fun readEscape(): Char {
            val escaped = text.getOrNull(position + 1)
            if (escaped == null) {
                if (strict) throw problem(KatachiGlobProblem.TrailingBackslash)
                return text[position++]
            }
            if (strict && escaped !in GLOB_ESCAPABLE) throw problem(KatachiGlobProblem.UnescapableCharacter(escaped))
            position += 2
            return escaped
        }

        private fun readTopLevelStar(): KatachiGlobToken {
            val start = position
            if (strict && text.getOrNull(position + 1) == '*') {
                val segment = segmentAround(position)
                if (segment != "**") throw problem(KatachiGlobProblem.DoubleStarInsideSegment(segment))
                position += 2
                return KatachiGlobToken.DoubleStar(start, position)
            }
            return KatachiGlobToken.Star(capturing = true, start, ++position)
        }

        private fun readClass(): KatachiGlobToken {
            val start = position
            position++
            val negated = text.getOrNull(position).let { it == '!' || it == '^' }
            if (negated) position++
            if (strict && text.getOrNull(position) == ']') throw problem(KatachiGlobProblem.EmptyCharacterClass)
            val members = mutableListOf<KatachiGlobClassMember>()
            while (true) {
                if (position >= text.length) {
                    if (strict) throw problem(KatachiGlobProblem.UnclosedBracket(segmentAround(start)))
                    break
                }
                if (text[position] == ']') {
                    position++
                    break
                }
                val from = readClassCharacter()
                val isRange = text.getOrNull(position) == '-' && position + 1 < text.length && text[position + 1] != ']'
                if (!isRange) {
                    members += KatachiGlobClassMember.Single(from)
                    continue
                }
                position++
                val to = readClassCharacter()
                if (strict && from > to) throw problem(KatachiGlobProblem.ReversedRange(from, to))
                members += KatachiGlobClassMember.Range(from, to)
            }
            return KatachiGlobToken.CharClass(negated, members, start, position)
        }

        private fun readClassCharacter(): Char {
            val character = text[position]
            if (character == '\\') return readEscape()
            if (strict && character == separator) throw problem(KatachiGlobProblem.SeparatorInBrackets(character))
            position++
            return character
        }

        private fun readBraces(): KatachiGlobToken {
            val start = position
            position++
            val alternatives = mutableListOf<List<KatachiGlobToken>>()
            var current = mutableListOf<KatachiGlobToken>()
            while (true) {
                if (position >= text.length) {
                    if (strict) throw problem(KatachiGlobProblem.UnclosedBrace(segmentAround(start)))
                    alternatives += current
                    break
                }
                val tokenStart = position
                when (val character = text[position]) {
                    '}' -> {
                        position++
                        if (strict && alternatives.isEmpty() && current.isEmpty()) {
                            throw problem(KatachiGlobProblem.EmptyBraces)
                        }
                        alternatives += current
                        break
                    }
                    ',' -> {
                        alternatives += current
                        current = mutableListOf()
                        position++
                    }
                    '\\' -> current += KatachiGlobToken.Literal(readEscape(), tokenStart, position)
                    '{' -> {
                        if (strict) throw problem(KatachiGlobProblem.NestedBraces)
                        current += KatachiGlobToken.Literal(character, tokenStart, ++position)
                    }
                    '*' -> {
                        if (strict && text.getOrNull(position + 1) == '*') throw problem(KatachiGlobProblem.DoubleStarInBraces)
                        current += KatachiGlobToken.Star(capturing = false, tokenStart, ++position)
                    }
                    '?' -> current += KatachiGlobToken.Question(tokenStart, ++position)
                    '[' -> current += readClass()
                    ']' -> {
                        if (strict) throw problem(KatachiGlobProblem.UnmatchedClosing(character))
                        current += KatachiGlobToken.Literal(character, tokenStart, ++position)
                    }
                    separator -> {
                        if (strict) throw problem(KatachiGlobProblem.SeparatorInBraces(character))
                        current += KatachiGlobToken.Literal(character, tokenStart, ++position)
                    }
                    else -> current += KatachiGlobToken.Literal(character, tokenStart, ++position)
                }
            }
            return KatachiGlobToken.Alternation(alternatives, start, position)
        }
    }
}
