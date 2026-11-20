package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import me.tbsten.katachi.check.internal.PrefixGlobIndex
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.test.pbt.checkTrials
import me.tbsten.katachi.test.pbt.pbtSeed
import me.tbsten.katachi.test.pbt.pbtTrials
import me.tbsten.katachi.test.pbt.pick
import me.tbsten.katachi.test.pbt.without

/*
 * `Glob` over its whole grammar (`**`, `{}`, `[!...]`, escapes), against a second reading of the
 * grammar that goes level by level and character by character, with no regular expression.
 *
 * GlobPathMatcherPropertySpec covers only the part of the syntax the JDK's PathMatcher reads the
 * same way. The reference here is the same reading the IDE plugin's port is checked against
 * (katachi-intellij-plugin `GlobReference.kt`), so katachi and the port answering like it is what
 * keeps the two in step beyond the hand-written rows of glob-cases.json.
 */

// region reference

/** Whether [pattern] matches [path], read level by level. Patterns stay inside what the generators write. */
private fun referenceMatches(pattern: String, path: String): Boolean = matchLevels(pattern.split('/'), path.split('/'))

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

/** Every way [level] reads once each `{a,b}` is written out. */
private fun sequencesOf(level: String): List<List<Position>> {
    var sequences = listOf(emptyList<Position>())
    var index = 0
    while (index < level.length) {
        when (val character = level[index]) {
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
                val close = level.indexOf(']', index + 2)
                val cls = classOf(level.substring(index + 1, close))
                sequences = sequences.map { it + cls }
                index = close + 1
            }

            '{' -> {
                val close = level.indexOf('}', index)
                val alternatives = level.substring(index + 1, close).split(',').flatMap { sequencesOf(it) }
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

        Position.Star -> (0..text.length).any { taken -> matchLevel(positions.drop(1), text.drop(taken)) }
    }
}

// endregion

// region generation

private val LITERAL_LEVELS = listOf("a", "b", "src", "Foo.kt", "x.y", "a\\*b", "a\\{b\\}", "a\\,b", "日本", "a+b(c)", "\$x^")

/** Levels whose only wildcard is a capturing `*`, so what each `*` took can be put back. */
private val STAR_LEVELS = listOf("*", "*.kt", "Foo*", "*Impl.kt", "a*", "*a*", "*.*", "a*\\*")

private val OTHER_WILDCARD_LEVELS = listOf(
    "Foo?", "?", "[a-c]*", "[!a]*", "[^b]?", "*[-x]", "*.{kt,kts}", "{a,b*}", "a{,b}", "{Foo,*Impl}.kt", "[ab]?", "[a-]",
)

private val PATH_LEVELS = listOf(
    "a", "b", "src", "Foo.kt", "x.y", "a*b", "a{b}", "a,b", "日本", "FooImpl.kt", "aa", "ba", "Foo", "Fooa", "Foo.kts", "bx", "x", "-",
    "ab", "a+b(c)", "\$x^", "Impl.kt", ".kt", "a.b", "a*",
)

/**
 * A valid pattern of 1 to 4 levels. Not two `**` in a row: `a/**/**` does not match `a` (the second
 * `**` keeps a separator in front of it), which glob-cases.json pins and this reference does not read.
 */
private fun Random.pattern(wildcards: List<String> = STAR_LEVELS + OTHER_WILDCARD_LEVELS): String {
    val chosen = mutableListOf<String>()
    repeat(nextInt(1, 5)) {
        chosen += when (nextInt(10)) {
            in 0..4 -> pick(LITERAL_LEVELS)
            in 5..7 -> pick(wildcards)
            else -> if (chosen.lastOrNull() == "**") pick(LITERAL_LEVELS) else "**"
        }
    }
    return chosen.joinToString("/")
}

private fun Random.path(): String = List(nextInt(1, 6)) { pick(PATH_LEVELS) }.joinToString("/")

/** Every pattern one level shorter than [pattern], keeping it valid. */
private fun shorterPatterns(pattern: String): List<String> {
    val levels = pattern.split('/')
    if (levels.size == 1) return emptyList()
    return levels.indices.map { levels.without(it).joinToString("/") }.filter { "**/**" !in it }
}

private fun shorterPaths(path: String): List<String> {
    val levels = path.split('/')
    if (levels.size == 1) return emptyList()
    return levels.indices.map { levels.without(it).joinToString("/") }
}

private data class GlobCase(val pattern: String, val path: String)

private fun GlobCase.smaller(): List<GlobCase> = shorterPatterns(pattern).map { copy(pattern = it) } + shorterPaths(path).map { copy(path = it) }

// endregion

/**
 * [pattern] with each capturing wildcard replaced by what it took ([groups], in order) and the
 * escapes undone: the path it matched, when `*` and `**` are its only wildcards.
 */
private fun refill(pattern: String, groups: List<String>): String {
    val values = groups.iterator()
    val levels = mutableListOf<String>()
    for (level in pattern.split('/')) {
        if (level == "**") {
            values.next().takeIf { it.isNotEmpty() }?.let { levels += it }
            continue
        }
        val text = StringBuilder()
        var index = 0
        while (index < level.length) {
            when (level[index]) {
                '\\' -> {
                    text.append(level[index + 1])
                    index += 2
                }

                '*' -> {
                    text.append(values.next())
                    index++
                }

                else -> text.append(level[index++])
            }
        }
        levels += text.toString()
    }
    return levels.joinToString("/")
}

class GlobReferencePropertySpec : FreeSpec({
    "どんな有効なパターンとパスでも、Glob の答えは階層ごとに読んだ文法と同じ" {
        checkTrials(
            "Glob.matches against the reference",
            trials = pbtTrials(3000),
            seed = pbtSeed(20261121L),
            generate = { random -> GlobCase(random.pattern(), random.path()) },
            shrinks = { it.smaller() },
        ) { (pattern, path) ->
            globClue(pattern, path) { Glob.compile(pattern).matches(path) shouldBe referenceMatches(pattern, path) }
        }
    }

    "パターンに当たるパスは literalPrefix の階層で始まる" {
        checkTrials(
            "literalPrefix",
            trials = pbtTrials(3000),
            seed = pbtSeed(20261122L),
            generate = { random ->
                val pattern = random.pattern()
                // Half the paths are cut from the pattern's own literal levels, so that matches are common.
                val path = if (random.nextBoolean()) {
                    pattern.split('/').joinToString("/") { level -> if (level in LITERAL_LEVELS) level.replace("\\", "") else random.pick(PATH_LEVELS) }
                } else {
                    random.path()
                }
                GlobCase(pattern, path)
            },
            shrinks = { it.smaller() },
        ) { (pattern, path) ->
            val glob = Glob.compile(pattern)
            if (glob.matches(path)) {
                globClue(pattern, path) { path.split('/').take(glob.literalPrefix.size) shouldBe glob.literalPrefix }
            }
        }
    }

    "* と ** だけのパターンは、各ワイルドカードが取った値を戻すと元のパスになる" {
        checkTrials(
            "what each wildcard took",
            trials = pbtTrials(3000),
            seed = pbtSeed(20261123L),
            generate = { random -> GlobCase(random.pattern(wildcards = STAR_LEVELS), random.path()) },
            shrinks = { it.smaller() },
        ) { (pattern, path) ->
            val groups = Glob.compile(pattern).matchGroups(path)
            globClue(pattern, path) {
                (groups != null) shouldBe referenceMatches(pattern, path)
                if (groups != null) refill(pattern, groups) shouldBe path
            }
        }
    }

    "PrefixGlobIndex が返すのは、全パターンを宣言順に試した答えと同じ" {
        checkTrials(
            "PrefixGlobIndex against every pattern in turn",
            trials = pbtTrials(800),
            seed = pbtSeed(20261124L),
            generate = { random -> List(random.nextInt(1, 13)) { random.pattern() } to List(random.nextInt(1, 7)) { random.path() } },
            shrinks = { (patterns, paths) ->
                patterns.indices.map { patterns.without(it) to paths }.filter { it.first.isNotEmpty() } +
                    paths.indices.map { patterns to paths.without(it) }.filter { it.second.isNotEmpty() }
            },
        ) { (patterns, paths) ->
            val index = PrefixGlobIndex(patterns) { Glob.compile(it) }
            for (path in paths) {
                val expected = patterns.filter { Glob.compile(it).matches(path) }
                globClue(patterns.toString(), path) {
                    index.matching(path) shouldBe expected
                    index.anyMatches(path) shouldBe expected.isNotEmpty()
                }
            }
        }
    }

    "記号が混ざったでたらめな文字列でも、投げる例外は KatachiGlobSyntaxException だけ" {
        val alphabet = "ab*/\\{}?[],.:!^-".toList()
        checkTrials(
            "only KatachiGlobSyntaxException",
            trials = pbtTrials(5000),
            seed = pbtSeed(20261125L),
            generate = { random -> List(random.nextInt(0, 9)) { random.pick(alphabet) }.joinToString("") },
            shrinks = { text -> text.indices.map { text.removeRange(it, it + 1) } },
        ) { pattern ->
            for (separator in listOf(Glob.PATH_SEPARATOR, Glob.MODULE_SEPARATOR)) {
                try {
                    val glob = Glob.compile(pattern, separator)
                    // A pattern that compiles has to answer for any path, without throwing either.
                    glob.matches("a/b")
                    glob.match("a:b")
                } catch (expected: KatachiGlobSyntaxException) {
                    expected.pattern shouldBe pattern
                }
            }
        }
    }
})

private inline fun globClue(pattern: String, path: String, block: () -> Unit) =
    io.kotest.assertions.withClue("pattern `$pattern` against `$path`", block)
