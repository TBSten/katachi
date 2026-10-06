package me.tbsten.katachi.intellij.uitest.pbt.definition

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list

/**
 * A second, independent reading of katachi's glob grammar, level by level (no regular expression), to compare
 * the port with. `*` is zero characters or more inside a level, `**` is zero levels or more, `?` is one character,
 * `[...]` / `[!...]` one character of a list or range (or not), `{a,b}` one of a few alternatives, `\x` the character
 * `x`. Patterns here stay inside what the generators write: no escape inside `[...]`, no nesting.
 */
internal fun referenceMatches(pattern: String, path: String): Boolean =
    matchLevels(pattern.split('/'), path.split('/'))

private fun matchLevels(pattern: List<String>, path: List<String>): Boolean {
    if (pattern.isEmpty()) return path.isEmpty()
    val head = pattern.first()
    if (head == "**") return (0..path.size).any { taken -> matchLevels(pattern.drop(1), path.drop(taken)) }
    if (path.isEmpty()) return false
    return sequencesOf(head).any { matchLevel(it, path.first()) } && matchLevels(pattern.drop(1), path.drop(1))
}

/** One position of a level: a character, `*`, `?`, or a character class. */
private sealed interface Position {
    data class Character(val value: Char) : Position
    data object Star : Position
    data object Question : Position
    data class Class(val negated: Boolean, val members: List<Pair<Char, Char>>) : Position
}

/** Every way [level] reads once each `{a,b}` is written out: a list of position sequences. */
private fun sequencesOf(level: String): List<List<Position>> {
    var sequences = listOf(emptyList<Position>())
    var index = 0
    while (index < level.length) {
        val character = level[index]
        when (character) {
            '\\' -> {
                sequences = sequences.map { it + Position.Character(level[index + 1]) }
                index += 2
            }
            '*' -> {
                sequences = sequences.map { it + Position.Star }
                index++
            }
            '?' -> {
                sequences = sequences.map { it + Position.Question }
                index++
            }
            '[' -> {
                val close = level.indexOf(']', index + 1)
                val cls = classOf(level.substring(index + 1, close))
                sequences = sequences.map { it + cls }
                index = close + 1
            }
            '{' -> {
                val close = level.indexOf('}', index)
                val alternatives = level.substring(index + 1, close).split(',').map { sequencesOf(it) }.flatten()
                sequences = sequences.flatMap { prefix -> alternatives.map { prefix + it } }
                index = close + 1
            }
            else -> {
                sequences = sequences.map { it + Position.Character(character) }
                index++
            }
        }
    }
    return sequences
}

private fun classOf(inside: String): Position.Class {
    val negated = inside.startsWith('!') || inside.startsWith('^')
    val body = if (negated) inside.drop(1) else inside
    val members = mutableListOf<Pair<Char, Char>>()
    var index = 0
    while (index < body.length) {
        if (index + 2 < body.length && body[index + 1] == '-') {
            members += body[index] to body[index + 2]
            index += 3
        } else {
            members += body[index] to body[index]
            index++
        }
    }
    return Position.Class(negated, members)
}

private fun matchLevel(positions: List<Position>, text: String): Boolean {
    if (positions.isEmpty()) return text.isEmpty()
    return when (val head = positions.first()) {
        is Position.Character -> text.isNotEmpty() && text.first() == head.value && matchLevel(positions.drop(1), text.drop(1))
        Position.Question -> text.isNotEmpty() && matchLevel(positions.drop(1), text.drop(1))
        is Position.Class -> text.isNotEmpty() &&
            (head.members.any { (from, to) -> text.first() in from..to } != head.negated) &&
            matchLevel(positions.drop(1), text.drop(1))
        // Zero characters or more, then the rest.
        Position.Star -> (0..text.length).any { taken -> matchLevel(positions.drop(1), text.drop(taken)) }
    }
}

private val literalLevels = listOf("a", "b", "src", "Foo.kt", "x.y", "a*b".replace("*", "\\*"), "日本")
private val wildcardLevels = listOf(
    "*", "*.kt", "Foo*", "*Impl.kt", "a*", "*a*", "Foo?", "?", "[a-c]*", "[!a]*", "*[-x]", "*.{kt,kts}", "{a,b*}", "a{,b}",
    "{Foo,*Impl}.kt", "[ab]?",
)

/** A valid pattern over a small alphabet: literal levels, wildcards in a level, and whole-level `**`. */
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

private val pathLevels = listOf("a", "b", "src", "Foo.kt", "x.y", "a*b", "日本", "FooImpl.kt", "aa", "ba", "Foo", "Fooa", "Foo.kts", "bx", "x", "-", "ab")

/** A path whose levels come from the same small alphabet, so patterns and paths often meet. */
internal val pathArb: Arb<String> = Arb.list(Arb.element(pathLevels), 1..5).let { levels -> arbitrary { levels.bind().joinToString("/") } }
