package me.tbsten.katachi.intellij.uitest.pbt.gutter

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.presentation.gutter.SiblingFiles
import me.tbsten.katachi.intellij.testing.layoutEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [SiblingFiles] (design 2.3, U-8) over random layouts: the files a menu lists are the role's other files, the ones beside
 * this file first, cut at the limit with the rest counted. `-Pkatachi.pbt.seed=N` tries another seed, `-Pkatachi.pbt.scale=N`
 * runs N times as many.
 */
class SiblingFilesPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261005L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (300 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    private val directories = listOf("a", "a/b", "a/b/c", "x", "x/y", "")

    private data class Scenario(val self: String, val entries: List<me.tbsten.katachi.intellij.model.LayoutEntryModel>, val limit: Int, val cutOff: Int)

    private fun scenario(random: Random): Scenario {
        fun file(): String = directories[random.nextInt(directories.size)].let { dir -> (if (dir.isEmpty()) "" else "$dir/") + "F${random.nextInt(30)}.kt" }
        val self = file()
        var cutOff = 0
        val entries = (0 until random.nextInt(0, 4)).map {
            val kind = LayoutEntryKind.known[random.nextInt(LayoutEntryKind.known.size)]
            val files = List(random.nextInt(0, 25)) { file() }
            val truncated = random.nextInt(4) == 0
            val entry = layoutEntry("p$it", kind, matchedFiles = files)
            if (truncated) {
                val extra = random.nextInt(1, 50)
                if (kind == LayoutEntryKind.File || kind == LayoutEntryKind.AnyFile) cutOff += extra
                entry.copy(matchedCount = files.size + extra, truncated = true)
            } else {
                entry
            }
        }
        return Scenario(self, entries, limit = random.nextInt(1, 20), cutOff = cutOff)
    }

    private fun holders(s: Scenario) = s.entries.filter { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }

    // covers: 並びと数
    @Test
    fun `載るのは他のファイルだけで 重ならず 上限を超えず 残りは数える`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val s = scenario(Random(caseSeed))
            val result = SiblingFiles.of(s.self, s.entries, s.limit)
            val others = holders(s).flatMap { it.matchedFiles }.distinct().filter { it != s.self }
            val where = "seed=$caseSeed $s -> $result"
            assertTrue(where, result.files.size <= s.limit)
            assertEquals(where, result.files.distinct(), result.files)
            assertFalse(where, s.self in result.files)
            assertTrue(where, others.containsAll(result.files))
            assertEquals(where, others.size + s.cutOff, result.files.size + result.more)
        }
    }

    // covers: 並びと数
    @Test
    fun `同じディレクトリのファイルが先で それぞれ見つけた順`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val s = scenario(Random(caseSeed))
            val result = SiblingFiles.of(s.self, s.entries, limit = 1000)
            val others = holders(s).flatMap { it.matchedFiles }.distinct().filter { it != s.self }
            val directory = SiblingFiles.parentOf(s.self)
            val expected = others.filter { SiblingFiles.parentOf(it) == directory } + others.filter { SiblingFiles.parentOf(it) != directory }
            assertEquals("seed=$caseSeed", expected, result.files)
        }
    }

    // covers: 表示
    @Test
    fun `短くしたディレクトリは 最初と最後の階層を残し 収まるなら収める`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val random = Random(caseSeed)
            val segments = List(random.nextInt(1, 9)) { "s${random.nextInt(100)}" + "x".repeat(random.nextInt(0, 12)) }
            val directory = segments.joinToString("/")
            val max = random.nextInt(8, 60)
            val shown = SiblingFiles.shortDirectory(directory, max)
            val where = "seed=$caseSeed $directory max=$max -> $shown"
            if (directory.length <= max) assertEquals(where, directory, shown)
            assertTrue(where, shown.startsWith(segments.first()))
            assertTrue(where, shown.endsWith(segments.last()))
            if (segments.size >= 3 && shown != directory) {
                val minimal = segments.first() + "/…/" + segments.last()
                assertTrue(where, shown.length <= max || shown == minimal)
            }
        }
    }
}
