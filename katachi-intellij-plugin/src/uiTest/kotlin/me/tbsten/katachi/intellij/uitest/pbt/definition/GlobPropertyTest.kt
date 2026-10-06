package me.tbsten.katachi.intellij.uitest.pbt.definition

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.definition.KatachiGlob
import me.tbsten.katachi.intellij.data.definition.KatachiGlobSyntaxException
import me.tbsten.katachi.intellij.data.definition.PrefixGlobIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The glob port over generated patterns and paths, against a level-by-level reading of the grammar. */
class GlobPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261005L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (400 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    // covers: 照合器
    @Test
    fun `どんな有効なパターンとパスでも移植の照合は階層ごとに読んだ文法と一致する`() = runBlocking<Unit> {
        checkAll(config, patternArb, pathArb) { pattern, path ->
            assertEquals("`$pattern` against `$path`", referenceMatches(pattern, path), KatachiGlob.compile(pattern).matches(path))
        }
    }

    // covers: 照合器
    @Test
    fun `パターンに当たるパスは literalPrefix で始まる`() = runBlocking<Unit> {
        checkAll(config, patternArb, pathArb) { pattern, path ->
            val glob = KatachiGlob.compile(pattern)
            if (glob.matches(path)) {
                val levels = path.split('/')
                assertTrue("`$pattern` matched `$path` but its prefix is ${glob.literalPrefix}", levels.take(glob.literalPrefix.size) == glob.literalPrefix)
            }
        }
    }

    // covers: 照合器
    @Test
    fun `ワイルドカードの無いパターンは同じ文字列のパスにだけ当たる`() = runBlocking<Unit> {
        checkAll(config, patternArb, pathArb) { pattern, path ->
            val glob = KatachiGlob.compile(pattern)
            if (!glob.hasWildcard) assertEquals("`$pattern` against `$path`", unescaped(pattern) == path, glob.matches(path))
        }
    }

    // covers: 索引
    @Test
    fun `索引が返すのは全パターンを順に試した答えと同じ`() = runBlocking<Unit> {
        checkAll(config, Arb.list(patternArb, 1..12), Arb.list(pathArb, 1..6)) { patterns, paths ->
            val index = PrefixGlobIndex(patterns) { KatachiGlob.compile(it) }
            for (path in paths) {
                val expected = patterns.filter { KatachiGlob.compile(it).matches(path) }
                assertEquals("$patterns against `$path`", expected, index.matching(path))
                assertEquals(expected.isNotEmpty(), index.anyMatches(path))
            }
        }
    }

    // covers: 文法エラー
    @Test
    fun `記号が混ざったでたらめな文字列も例外は KatachiGlobSyntaxException だけ`() = runBlocking<Unit> {
        val alphabet = "ab*/\\{}?[],.:!^-"
        checkAll(config, Arb.list(Arb.element(alphabet.toList()), 0..8)) { chars ->
            val pattern = chars.joinToString("")
            try {
                KatachiGlob.compile(pattern)
            } catch (expected: KatachiGlobSyntaxException) {
                assertEquals(pattern, expected.pattern)
            }
        }
    }

    private fun unescaped(pattern: String): String = pattern.replace("\\*", "*")
}
