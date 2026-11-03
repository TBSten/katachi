package me.tbsten.katachi.intellij.data.definition

// Port of katachi's `Glob`; `glob-cases.json` pins the two together.
internal enum class GlobGroupKind { Single, Recursive }

internal class KatachiGlob private constructor(
    val pattern: String,
    val separator: Char,
    private val regex: Regex,
    val groupKinds: List<GlobGroupKind>,
    val groupSegments: List<Int>,
    val literalPrefix: List<String>,
    val hasWildcard: Boolean,
) {
    val singleWildcardCount: Int get() = groupKinds.count { it == GlobGroupKind.Single }

    fun matches(path: String): Boolean = regex.matches(path)

    fun matchGroups(path: String): List<String>? {
        val result = regex.matchEntire(path) ?: return null
        return groupKinds.indices.map { index -> result.groupValues.getOrElse(index + 1) { "" } }
    }

    fun matchRanges(path: String): List<IntRange?>? {
        val result = regex.matchEntire(path) ?: return null
        return groupKinds.indices.map { index -> result.groups[index + 1]?.range }
    }

    fun singleGroupIndex(segmentIndex: Int, wildcardIndex: Int): Int? =
        groupKinds.indices.filter { groupKinds[it] == GlobGroupKind.Single && groupSegments[it] == segmentIndex }.getOrNull(wildcardIndex)

    fun singleGroupValue(groups: List<String>, segmentIndex: Int, wildcardIndex: Int): String? {
        val singles = groupKinds.indices.filter { groupKinds[it] == GlobGroupKind.Single && groupSegments[it] == segmentIndex }
        return singles.getOrNull(wildcardIndex)?.let { groups.getOrNull(it) }
    }

    override fun equals(other: Any?): Boolean =
        other is KatachiGlob && other.pattern == pattern && other.separator == separator

    override fun hashCode(): Int = 31 * pattern.hashCode() + separator.hashCode()

    override fun toString(): String = "KatachiGlob($pattern)"

    companion object {
        const val PATH_SEPARATOR: Char = '/'
        const val MODULE_SEPARATOR: Char = ':'

        fun compile(pattern: String, separator: Char = PATH_SEPARATOR): KatachiGlob {
            if (pattern.isEmpty()) throw KatachiGlobSyntaxException(pattern, KatachiGlobProblem.EmptyPattern)

            val tokens = KatachiGlobLexer.scan(pattern, separator)
            // A leading separator is the pattern being rooted (`:feature:*`), not an empty
            // first segment.
            val leadingSeparator = tokens.first() is KatachiGlobToken.Separator
            val segmentTokens = splitIntoSegments(if (leadingSeparator) tokens.drop(1) else tokens)
            if (segmentTokens.any { it.isEmpty() }) {
                throw KatachiGlobSyntaxException(pattern, KatachiGlobProblem.EmptySegment(separator))
            }

            val escapedSeparator = escapeForRegex(separator)
            val oneLevel = "[^$escapedSeparator]+"
            val manyLevels = "($oneLevel(?:$escapedSeparator$oneLevel)*)"
            val kinds = mutableListOf<GlobGroupKind>()
            val groupSegments = mutableListOf<Int>()
            val expression = StringBuilder()
            if (leadingSeparator) expression.append(escapedSeparator)

            segmentTokens.forEachIndexed { index, level ->
                val groupsBefore = kinds.size
                // A preceding `**` has already emitted the separator that follows it, inside
                // its own optional group, so that `a/**/b` still matches `a/b`.
                val needsSeparator = index > 0 && segmentTokens[index - 1].singleOrNull() !is KatachiGlobToken.DoubleStar
                if (level.singleOrNull() is KatachiGlobToken.DoubleStar) {
                    kinds += GlobGroupKind.Recursive
                    if (index == segmentTokens.lastIndex) {
                        expression.append(
                            if (needsSeparator) "(?:$escapedSeparator$manyLevels)?" else "$manyLevels?",
                        )
                    } else {
                        if (needsSeparator) expression.append(escapedSeparator)
                        expression.append("(?:$manyLevels$escapedSeparator)?")
                    }
                } else {
                    if (needsSeparator) expression.append(escapedSeparator)
                    expression.append(tokensExpression(level, escapedSeparator, kinds))
                }
                repeat(kinds.size - groupsBefore) { groupSegments += index }
            }

            return KatachiGlob(
                pattern = pattern,
                separator = separator,
                regex = Regex(expression.toString()),
                groupKinds = kinds.toList(),
                groupSegments = groupSegments.toList(),
                literalPrefix = if (leadingSeparator) emptyList() else literalPrefixOf(segmentTokens),
                hasWildcard = tokens.any { it !is KatachiGlobToken.Literal && it !is KatachiGlobToken.Separator },
            )
        }

        private fun splitIntoSegments(tokens: List<KatachiGlobToken>): List<List<KatachiGlobToken>> {
            val segments = mutableListOf<List<KatachiGlobToken>>()
            var current = mutableListOf<KatachiGlobToken>()
            for (token in tokens) {
                if (token is KatachiGlobToken.Separator) {
                    segments += current
                    current = mutableListOf()
                } else {
                    current += token
                }
            }
            segments += current
            return segments
        }

        private const val REGEX_METACHARACTERS: String = "\\.[]{}()*+-?^$|&/<>"

        private fun literalPrefixOf(segments: List<List<KatachiGlobToken>>): List<String> {
            val prefix = mutableListOf<String>()
            for (segment in segments) {
                if (!segment.all { it is KatachiGlobToken.Literal }) break
                prefix += segment.joinToString("") { (it as KatachiGlobToken.Literal).character.toString() }
            }
            return prefix
        }

        private fun escapeForRegex(character: Char): String =
            if (character in REGEX_METACHARACTERS) "\\$character" else character.toString()

        private fun tokensExpression(
            tokens: List<KatachiGlobToken>,
            escapedSeparator: String,
            kinds: MutableList<GlobGroupKind>,
        ): String {
            val expression = StringBuilder()
            for (token in tokens) {
                when (token) {
                    is KatachiGlobToken.Literal -> expression.append(escapeForRegex(token.character))
                    is KatachiGlobToken.Star -> if (token.capturing) {
                        // Zero characters or more, never crossing a separator.
                        expression.append("([^$escapedSeparator]*)")
                        kinds += GlobGroupKind.Single
                    } else {
                        expression.append("[^$escapedSeparator]*")
                    }
                    is KatachiGlobToken.Question -> expression.append("[^$escapedSeparator]")
                    is KatachiGlobToken.CharClass -> expression.append(classExpression(token, escapedSeparator))
                    is KatachiGlobToken.Alternation -> expression.append(
                        token.alternatives.joinToString("|", prefix = "(?:", postfix = ")") {
                            tokensExpression(it, escapedSeparator, kinds)
                        },
                    )
                    // The scanner keeps these out of a segment's tokens (and a `**` is handled by the caller).
                    is KatachiGlobToken.DoubleStar, is KatachiGlobToken.Separator -> Unit
                }
            }
            return expression.toString()
        }

        private fun classExpression(token: KatachiGlobToken.CharClass, escapedSeparator: String): String {
            val members = token.members.joinToString("") { member ->
                when (member) {
                    is KatachiGlobClassMember.Single -> escapeForRegex(member.character)
                    is KatachiGlobClassMember.Range -> "${escapeForRegex(member.from)}-${escapeForRegex(member.to)}"
                }
            }
            return when {
                token.negated -> "[^$members$escapedSeparator]"
                // A range can run across the separator's code point, which must still not match.
                token.members.any { it is KatachiGlobClassMember.Range } -> "[$members&&[^$escapedSeparator]]"
                else -> "[$members]"
            }
        }
    }
}
