package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.presentation.gutter.RoleFileCount
import me.tbsten.katachi.intellij.testing.layoutEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/** P-5 of ide-authoring: the words under a layout line, for every combination of a count, a cut list, `required` and age. */
class CountTextTest {
    private fun entry(files: Int?, required: Boolean = false, cut: Boolean = false): LayoutEntryModel {
        val listed = List(files?.coerceAtMost(LISTED) ?: 0) { "a/F$it.kt" }
        return layoutEntry("a/*.kt", required = required, matchedFiles = listed).copy(matchedCount = files, truncated = cut)
    }

    @Test
    fun `件数の言い方は 0 1 2以上 分からない 切られた の 5 通りがあり required は 0 件のときだけ言い方を変える`() {
        val rows = listOf(
            Triple(0, false, "no files"),
            Triple(0, true, "missing (required)"),
            Triple(1, false, "1 file"),
            Triple(1, true, "1 file"),
            Triple(3, false, "3 files"),
            Triple(3, true, "3 files"),
        )
        for ((count, required, expected) in rows) {
            assertEquals("count=$count required=$required", expected, CountText.of(listOf(entry(count, required)), stale = false))
        }
        assertEquals("? files", CountText.of(listOf(entry(null)), stale = false))
        assertEquals("? files", CountText.of(listOf(entry(null, required = true)), stale = false))
    }

    @Test
    fun `一覧が切られた行は 並んだファイルの数に プラスを付ける`() {
        assertEquals("$LISTED+ files", CountText.of(listOf(entry(900, cut = true)), stale = false))
        assertEquals("$LISTED+ files", CountText.of(listOf(entry(900, required = true, cut = true)), stale = false))
    }

    @Test
    fun `古い答えは 末尾に stale が付き 全部の組み合わせで 付くか付かないかだけが違う`() {
        val entries = listOf(entry(0), entry(0, required = true), entry(1), entry(3), entry(null), entry(900, cut = true))
        for (entry in entries) {
            val fresh = CountText.of(listOf(entry), stale = false)
            assertEquals("$fresh (stale)", CountText.of(listOf(entry), stale = true))
        }
    }

    @Test
    fun `wildcard の module key で宣言が複数の entry になったら 合計を言い modules の数を stale の前に付ける`() {
        val two = listOf(entry(2), entry(1).copy(matchedFiles = listOf("b/G.kt")))
        assertEquals("3 files (2 modules)", CountText.of(two, stale = false, modules = 2))
        assertEquals("3 files (2 modules) (stale)", CountText.of(two, stale = true, modules = 2))
        assertEquals("3 files", CountText.of(two, stale = false, modules = 1))
    }

    @Test
    fun `どの組み合わせでも 数え方は RoleFileCount と同じで 件数の言葉はその数から作られる`() {
        for (count in listOf(null, 0, 1, 2, 7)) {
            for (required in listOf(false, true)) {
                val entries = listOf(entry(count, required))
                val number = RoleFileCount.of(entries)
                val text = CountText.of(entries, stale = false)
                when (number) {
                    null -> assertEquals("? files", text)
                    0 -> assertEquals(if (required) "missing (required)" else "no files", text)
                    else -> assertEquals(CountText.filesText(number), text)
                }
            }
        }
    }

    @Test
    fun `file entry の無い行は 0 件として扱う`() {
        val directory = layoutEntry("a", kind = LayoutEntryKind.Directory)
        assertEquals("no files", CountText.of(listOf(directory), stale = false))
    }

    @Test
    fun `役割の ◆ の tooltip と同じ言い方を 使う`() {
        for (count in listOf(null, 0, 1, 5)) assertEquals(CountText.filesText(count), DefinitionGutterMarks.filesText(count))
    }

    private companion object {
        /** How many files the test lists at most, like the JSON's cap. */
        const val LISTED = 5
    }
}
