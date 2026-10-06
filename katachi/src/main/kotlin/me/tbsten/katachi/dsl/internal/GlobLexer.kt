package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.KatachiGlobSyntaxException

/**
 * One piece of a glob pattern, with where it was written ([start] inclusive, [end] exclusive).
 *
 * [Star] and [DoubleStar] are the only pieces whose matched text is handed back as a value;
 * [Question], [CharClass] and [Alternation] only take part in matching.
 */
internal sealed interface GlobToken {
    val start: Int
    val end: Int

    /** A character that means itself, with its escape already undone. */
    class Literal(val character: Char, override val start: Int, override val end: Int) : GlobToken

    /** `*`. [capturing] is false inside `{...}`, where the matched text is never read back. */
    class Star(val capturing: Boolean, override val start: Int, override val end: Int) : GlobToken

    /** `**` written as a whole segment. */
    class DoubleStar(override val start: Int, override val end: Int) : GlobToken

    /** `?`. */
    class Question(override val start: Int, override val end: Int) : GlobToken

    /** `[abc]`, `[a-z]`, `[!abc]` or `[^abc]`. */
    class CharClass(
        val negated: Boolean,
        val members: List<GlobClassMember>,
        override val start: Int,
        override val end: Int,
    ) : GlobToken

    /** `{a,b,c}`. Each alternative is a list of tokens that are never separators or `**`. */
    class Alternation(
        val alternatives: List<List<GlobToken>>,
        override val start: Int,
        override val end: Int,
    ) : GlobToken

    /** The level separator, only ever at the top level. */
    class Separator(override val start: Int, override val end: Int) : GlobToken
}

/** One entry of a `[...]`: a single character or a `from-to` range. */
internal sealed interface GlobClassMember {
    class Single(val character: Char) : GlobClassMember
    class Range(val from: Char, val to: Char) : GlobClassMember
}

/**
 * Reads katachi's glob syntax, in one place.
 *
 * [Glob.compile] reads strictly and raises a [GlobProblem] for anything it cannot read. Every
 * other caller (counting the `*` that capture a value, filling one in, expanding `{a,b}`)
 * reads leniently, so a pattern that [Glob.compile] would refuse never throws from here:
 * an unclosed `{` or `[` runs to the end of the text, and an escape that escapes nothing is
 * kept as written.
 *
 * ## Example 1: count the `*` that capture, leaving out the ones inside `{...}`
 * ```kt
 * GlobLexer.capturingStarCount("*{Screen,*Route}") shouldBe 1
 * ```
 */
internal object GlobLexer {
    private const val MAX_EXPANSION: Int = 64

    /** Reads [text] strictly, splitting levels on [separator]. Throws [KatachiGlobSyntaxException]. */
    fun scan(text: String, separator: Char): List<GlobToken> = Scanner(text, separator, strict = true).run()

    /** Reads [text] without throwing. [separator] is only needed to tell where a level ends. */
    fun scanLeniently(text: String, separator: Char? = null): List<GlobToken> =
        Scanner(text, separator, strict = false).run()

    /** How many `*` in [segment] stand for a value: not inside `{...}` or `[...]`, not escaped. */
    fun capturingStarCount(segment: String): Int =
        scanLeniently(segment).count { it is GlobToken.Star && it.capturing }

    /** Whether [text] holds an unescaped `*`, `?`, `[` or `{`. */
    fun hasGlobWildcard(text: String): Boolean = scanLeniently(text).any { it.isWildcard() }

    /** Whether [text] holds an unescaped `?`, `[` or `{`, which can never be given a name. */
    fun hasNonCapturingWildcard(text: String): Boolean =
        scanLeniently(text).any { it is GlobToken.Question || it is GlobToken.CharClass || it is GlobToken.Alternation }

    /**
     * Replaces the n-th capturing `*` of [segment] (0-based) with what [replacement] answers
     * for n, keeping the `*` when it answers `null`. Everything else stays as written.
     */
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

    /**
     * Writes out every `{a,b}` of [pattern] as the strings it stands for, or `null` when there
     * would be more than 64 of them. Nothing else is touched, so a result may still hold `*`,
     * `?` and `[...]`.
     */
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

    /** [text] with each `\x` read as `x`. */
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

        /** The level around [index], as far as the separators can be told apart. */
        private fun segmentAround(index: Int): String {
            val from = separator?.let { text.lastIndexOf(it, index - 1) + 1 } ?: 0
            val to = separator?.let { text.indexOf(it, index).takeIf { found -> found >= 0 } } ?: text.length
            return text.substring(from, to)
        }

        /** Reads `\x` at [position] and returns `x`. A lone or needless `\` is an error when strict. */
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
