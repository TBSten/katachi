package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.nio.file.FileSystems
import kotlin.random.Random
import me.tbsten.katachi.dsl.internal.Glob

/*
 * The claim "katachi's glob follows the standard one" checked against the JDK itself: for the
 * part of the syntax where the two mean the same thing (no `**`, no `[^`, no `\` inside a
 * class, no `{}`), `Glob.matches` has to give `PathMatcher`'s answer. A generated pattern and a
 * generated path rarely meet by chance, so the paths are cut from the same small alphabet.
 */

private val LETTERS: List<Char> = listOf('a', 'b', 'c', '.', '-', '_')

private val SEGMENT_PIECES: List<String> = listOf(
    "a", "b", "c", ".", "-", "_", "*", "?", "[ab]", "[a-c]", "[!a]", "[!b-c]", "[-a]", "[a-]", "{a,bc}",
    "{*a,b?}", "{a,}", "{[ab],c*}", "{a,b,c}",
)

private fun Random.pattern(): String {
    val levels = nextInt(1, 4)
    return (1..levels).joinToString("/") { (1..nextInt(1, 4)).joinToString("") { SEGMENT_PIECES.random(this) } }
}

private fun Random.path(): String {
    val levels = nextInt(1, 4)
    return (1..levels).joinToString("/") { (1..nextInt(1, 5)).joinToString("") { LETTERS.random(this).toString() } }
}

private fun String.hasDotOnlyLevel(): Boolean = split('/').any { level -> level.all { it == '.' } }

class GlobPathMatcherPropertySpec : FreeSpec({
    "PathMatcher と意味が重なるパターンでは Glob の答えが PathMatcher と同じ" {
        val random = Random(20261006)
        var matched = 0
        var rejected = 0
        repeat(4000) {
            val pattern = random.pattern()
            // `**` is katachi's own whole-level wildcard; in the JDK it also crosses levels.
            if ("**" in pattern) return@repeat
            val glob = Glob.compile(pattern)
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            repeat(12) {
                val path = random.path()
                if (path.hasDotOnlyLevel()) return@repeat
                val expected = matcher.matches(java.nio.file.Paths.get(path))
                if (expected) matched++ else rejected++
                withClue(pattern, path) { glob.matches(path) shouldBe expected }
            }
        }
        // The check is only worth something when both answers actually occurred.
        matched shouldBeGreaterThan 300
        rejected shouldBeGreaterThan 300
    }
})

private inline fun withClue(pattern: String, path: String, block: () -> Unit) =
    io.kotest.assertions.withClue("pattern=`$pattern` path=`$path`", block)
