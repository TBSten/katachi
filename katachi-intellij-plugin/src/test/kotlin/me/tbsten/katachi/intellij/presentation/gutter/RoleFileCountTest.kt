package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The number of files a role holds, which the definition ◆ and the source file's menu must say the same. */
class RoleFileCountTest {
    private fun entry(vararg files: String, truncated: Boolean = false, count: Int? = files.size, kind: LayoutEntryKind = LayoutEntryKind.File): LayoutEntryModel =
        layoutEntry("a/*.kt", kind = kind, matchedFiles = files.toList()).copy(truncated = truncated, matchedCount = count)

    @Test
    fun `重なったファイルは 1 回だけ数える`() {
        assertEquals(3, RoleFileCount.of(listOf(entry("a/X.kt", "a/Y.kt"), entry("a/Y.kt", "a/Z.kt"))))
    }

    @Test
    fun `一覧が切られた entry は 隠れた分を足す`() {
        assertEquals(900, RoleFileCount.of(listOf(entry("a/X.kt", "a/Y.kt", truncated = true, count = 900))))
    }

    @Test
    fun `切られた entry と重なる別の entry があっても 重複は切られた一覧の中でだけ除く`() {
        assertEquals(2 + 98 + 1, RoleFileCount.of(listOf(entry("a/X.kt", "a/Y.kt", truncated = true, count = 100), entry("a/Y.kt", "a/Z.kt"))))
    }

    @Test
    fun `file entry が無い役割は 0 件`() {
        assertEquals(0, RoleFileCount.of(listOf(entry(kind = LayoutEntryKind.Directory, count = null))))
        assertEquals(0, RoleFileCount.of(emptyList()))
    }

    @Test
    fun `file entry の件数が全部分からないときは 0 件ではなく 分からない`() {
        assertNull(RoleFileCount.of(listOf(entry(count = null), entry(kind = LayoutEntryKind.AnyFile, count = null))))
    }

    @Test
    fun `件数の分かる entry が1つでもあれば その件数を言う`() {
        assertEquals(1, RoleFileCount.of(listOf(entry(count = null), entry("a/X.kt"))))
    }

    @Test
    fun `土台のメニューの SiblingFiles と 同じ数を数える`() {
        val shapes = listOf(
            listOf(entry("a/X.kt", "a/Y.kt"), entry("a/Y.kt", "a/Z.kt")),
            listOf(entry("a/X.kt", "a/Y.kt", truncated = true, count = 50), entry("b/Y.kt")),
            listOf(entry("a/X.kt", truncated = true, count = 3), entry("a/Y.kt", truncated = true, count = 9), entry(kind = LayoutEntryKind.Directory, count = null)),
            listOf(entry("a/X.kt", kind = LayoutEntryKind.AnyFile)),
        )
        for (entries in shapes) {
            val siblings = SiblingFiles.of("", entries, limit = Int.MAX_VALUE)
            assertEquals("$entries", siblings.files.size + siblings.more, RoleFileCount.of(entries))
        }
    }
}
