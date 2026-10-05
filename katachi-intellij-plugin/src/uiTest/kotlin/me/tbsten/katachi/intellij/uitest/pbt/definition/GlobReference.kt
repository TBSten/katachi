package me.tbsten.katachi.intellij.uitest.pbt.definition

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list

/**
 * A second, independent reading of katachi's glob grammar, level by level (no regular expression), to compare
 * the port with. `*` is one or more characters inside a level, `**` is zero levels or more, `\x` is the character
 * `x`. Patterns here never use another metacharacter.
 */
internal fun referenceMatches(pattern: String, path: String): Boolean =
    matchLevels(pattern.split('/'), path.split('/'))

private fun matchLevels(pattern: List<String>, path: List<String>): Boolean {
    if (pattern.isEmpty()) return path.isEmpty()
    val head = pattern.first()
    if (head == "**") return (0..path.size).any { taken -> matchLevels(pattern.drop(1), path.drop(taken)) }
    if (path.isEmpty()) return false
    return matchLevel(tokensOf(head), path.first()) && matchLevels(pattern.drop(1), path.drop(1))
}

/** `null` is a `*`; a character is itself. */
private fun tokensOf(level: String): List<Char?> {
    val tokens = mutableListOf<Char?>()
    var index = 0
    while (index < level.length) {
        if (level[index] == '\\') {
            tokens += level[index + 1]
            index += 2
        } else {
            tokens += if (level[index] == '*') null else level[index]
            index++
        }
    }
    return tokens
}

private fun matchLevel(tokens: List<Char?>, text: String): Boolean {
    if (tokens.isEmpty()) return text.isEmpty()
    val head = tokens.first()
    if (head != null) return text.isNotEmpty() && text.first() == head && matchLevel(tokens.drop(1), text.drop(1))
    // One or more characters, then the rest.
    return (1..text.length).any { taken -> matchLevel(tokens.drop(1), text.drop(taken)) }
}

private val literalLevels = listOf("a", "b", "src", "Foo.kt", "x.y", "a*b".replace("*", "\\*"), "日本")
private val wildcardLevels = listOf("*", "*.kt", "Foo*", "*Impl.kt", "a*", "*a*")

/** A valid pattern over a small alphabet: literal levels, `*` in a level, and whole-level `**`. */
internal val patternArb: Arb<String> = arbitrary {
    val levels = Arb.int(1..4).bind()
    val chosen = mutableListOf<String>()
    repeat(levels) {
        chosen += when (Arb.int(0..9).bind()) {
            in 0..4 -> Arb.element(literalLevels).bind()
            in 5..7 -> Arb.element(wildcardLevels).bind()
            // Not two `**` in a row: katachi's own `Glob` does not read `a/**/**` as matching `a` (a mandatory separator
            // is left in front of the second one), which the port keeps and the conformance cases pin.
            else -> if (chosen.lastOrNull() == "**") Arb.element(literalLevels).bind() else "**"
        }
    }
    chosen.joinToString("/")
}

private val pathLevels = listOf("a", "b", "src", "Foo.kt", "x.y", "a*b", "日本", "FooImpl.kt", "aa", "ba")

/** A path whose levels come from the same small alphabet, so patterns and paths often meet. */
internal val pathArb: Arb<String> = Arb.list(Arb.element(pathLevels), 1..5).let { levels -> arbitrary { levels.bind().joinToString("/") } }
