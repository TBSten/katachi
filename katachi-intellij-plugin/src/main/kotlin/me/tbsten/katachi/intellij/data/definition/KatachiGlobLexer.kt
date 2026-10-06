package me.tbsten.katachi.intellij.data.definition

/**
 * One piece of a glob pattern, with where it was written ([start] inclusive, [end] exclusive).
 *
 * [Star] and [DoubleStar] are the only pieces whose matched text is handed back as a value;
 * [Question], [CharClass] and [Alternation] only take part in matching.
 */
internal sealed interface KatachiGlobToken {
    val start: Int
    val end: Int

    /** A character that means itself, with its escape already undone. */
    class Literal(val character: Char, override val start: Int, override val end: Int) : KatachiGlobToken

    /** `*`. [capturing] is false inside `{...}`, where the matched text is never read back. */
    class Star(val capturing: Boolean, override val start: Int, override val end: Int) : KatachiGlobToken

    /** `**` written as a whole segment. */
    class DoubleStar(override val start: Int, override val end: Int) : KatachiGlobToken

    /** `?`. */
    class Question(override val start: Int, override val end: Int) : KatachiGlobToken

    /** `[abc]`, `[a-z]`, `[!abc]` or `[^abc]`. */
    class CharClass(
        val negated: Boolean,
        val members: List<KatachiGlobClassMember>,
        override val start: Int,
        override val end: Int,
    ) : KatachiGlobToken

    /** `{a,b,c}`. Each alternative is a list of tokens that are never separators or `**`. */
    class Alternation(
        val alternatives: List<List<KatachiGlobToken>>,
        override val start: Int,
        override val end: Int,
    ) : KatachiGlobToken

    /** The level separator, only ever at the top level. */
    class Separator(override val start: Int, override val end: Int) : KatachiGlobToken
}

/** One entry of a `[...]`: a single character or a `from-to` range. */
internal sealed interface KatachiGlobClassMember {
    class Single(val character: Char) : KatachiGlobClassMember
    class Range(val from: Char, val to: Char) : KatachiGlobClassMember
}

/**
 * A port of katachi's `GlobLexer` (`me.tbsten.katachi.dsl.internal.GlobLexer`): the same syntax read the same way,
 * so the IDE and the check agree on what a pattern means. `glob-cases.json` pins the two together.
 *
 * [KatachiGlob.compile] reads strictly and raises a [KatachiGlobProblem] for anything it cannot read. Every
 * other caller (counting the `*` that capture a value, filling one in, expanding `{a,b}`)
 * reads leniently, so a pattern that [KatachiGlob.compile] would refuse never throws from here:
 * an unclosed `{` or `[` runs to the end of the text, and an escape that escapes nothing is
 * kept as written.
 *
 * ## Example 1: count the `*` that capture, leaving out the ones inside `{...}`
 * ```kt
 * KatachiGlobLexer.capturingStarCount("*{Screen,*Route}") shouldBe 1
 * ```
 */
internal object KatachiGlobLexer {
    private const val MAX_EXPANSION: Int = 64

    /** Reads [text] strictly, splitting levels on [separator]. Throws [KatachiGlobSyntaxException]. */
    fun scan(text: String, separator: Char): List<KatachiGlobToken> = Scanner(text, separator, strict = true).run()

    /** Reads [text] without throwing. [separator] is only needed to tell where a level ends. */
    fun scanLeniently(text: String, separator: Char? = null): List<KatachiGlobToken> =
        Scanner(text, separator, strict = false).run()

    /** How many `*` in [segment] stand for a value: not inside `{...}` or `[...]`, not escaped. */
    fun capturingStarCount(segment: String): Int =
        scanLeniently(segment).count { it is KatachiGlobToken.Star && it.capturing }

    /** Whether [text] holds an unescaped `*`, `?`, `[` or `{`. */
    fun hasGlobWildcard(text: String): Boolean = scanLeniently(text).any { it.isWildcard() }

    /** Whether [text] holds an unescaped `?`, `[` or `{`, which can never be given a name. */
    fun hasNonCapturingWildcard(text: String): Boolean =
        scanLeniently(text).any { it is KatachiGlobToken.Question || it is KatachiGlobToken.CharClass || it is KatachiGlobToken.Alternation }

    /**
     * Replaces the n-th capturing `*` of [segment] (0-based) with what [replacement] answers
     * for n, keeping the `*` when it answers `null`. Everything else stays as written.
     */
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

    /**
     * Writes out every `{a,b}` of [pattern] as the strings it stands for, or `null` when there
     * would be more than 64 of them. Nothing else is touched, so a result may still hold `*`,
     * `?` and `[...]`.
     */
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
