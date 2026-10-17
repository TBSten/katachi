package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.testing.layoutEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/** The other files of a role the gutter menu lists (design 2.3, U-8): their order, the cut at 15, and the `… and n more`. */
class SiblingFilesTest {
    private val self = "src/main/kotlin/com/example/service/HealthService.kt"

    private fun siblings(vararg entries: List<String>, limit: Int = SiblingFiles.LIMIT) =
        SiblingFiles.of(self, entries.map { layoutEntry("**.kt", LayoutEntryKind.AnyFile, matchedFiles = it) }, limit)

    @Test
    fun `同じディレクトリのファイルが先で 残りは見つけた順`() {
        val result = siblings(
            listOf(
                "src/main/kotlin/com/example/other/Alpha.kt",
                "src/main/kotlin/com/example/service/Legacy.kt",
                self,
                "src/main/kotlin/com/example/api/Beta.kt",
                "src/main/kotlin/com/example/service/Newer.kt",
            ),
        )
        assertEquals(
            listOf(
                "src/main/kotlin/com/example/service/Legacy.kt",
                "src/main/kotlin/com/example/service/Newer.kt",
                "src/main/kotlin/com/example/other/Alpha.kt",
                "src/main/kotlin/com/example/api/Beta.kt",
            ),
            result.files,
        )
        assertEquals(0, result.more)
    }

    @Test
    fun `このファイル自身は載らず 複数の entry に重なるファイルは 1 回だけ載る`() {
        val shared = "src/main/kotlin/com/example/service/Legacy.kt"
        val result = siblings(listOf(self, shared), listOf(shared, "a/B.kt"))
        assertEquals(listOf(shared, "a/B.kt"), result.files)
    }

    @Test
    fun `ファイルを持たない entry の matchedFiles は数えない`() {
        val entries = listOf(
            layoutEntry("a", LayoutEntryKind.Directory, matchedFiles = listOf("a/X.kt")),
            layoutEntry("b/**", LayoutEntryKind.Ignore, matchedFiles = listOf("b/Y.kt")),
            layoutEntry("c.kt", LayoutEntryKind.File, matchedFiles = listOf("c.kt")),
        )
        assertEquals(listOf("c.kt"), SiblingFiles.of(self, entries).files)
    }

    @Test
    fun `15 件で打ち切り 残りの数を言う`() {
        val files = (1..40).map { "dir/F$it.kt" }
        val result = siblings(files)
        assertEquals(files.take(15), result.files)
        assertEquals(25, result.more)
    }

    @Test
    fun `ちょうど 15 件なら残りは 0`() {
        val result = siblings((1..15).map { "dir/F$it.kt" })
        assertEquals(15, result.files.size)
        assertEquals(0, result.more)
    }

    @Test
    fun `見つけた時点で切れた entry は 切れた分を残りに足す`() {
        val cut = layoutEntry("**.kt", LayoutEntryKind.AnyFile, matchedFiles = (1..10).map { "dir/F$it.kt" })
            .copy(matchedCount = 300, truncated = true)
        val result = SiblingFiles.of(self, listOf(cut), limit = 4, holding = listOf(cut))
        assertEquals(4, result.files.size)
        assertEquals(6 + 290 - 1, result.more) // self is not in the 10 listed: it is one of the 290 cut, and not its own sibling
    }

    @Test
    fun `開いているファイルが切られていない側にあれば 切れた分をそのまま足す`() {
        val cut = layoutEntry("**.kt", LayoutEntryKind.AnyFile, matchedFiles = listOf(self) + (1..9).map { "dir/F$it.kt" })
            .copy(matchedCount = 300, truncated = true)
        val result = SiblingFiles.of(self, listOf(cut), limit = 4)
        assertEquals(4, result.files.size)
        assertEquals(5 + 290, result.more)
    }

    @Test
    fun `開いているファイルが別の entry の一覧にあれば 切れた entry の分は減らさない`() {
        val cut = layoutEntry("**.kt", LayoutEntryKind.AnyFile, matchedFiles = (1..10).map { "dir/F$it.kt" })
            .copy(matchedCount = 300, truncated = true)
        val other = layoutEntry("S.kt", LayoutEntryKind.File, matchedFiles = listOf(self))
        val result = SiblingFiles.of(self, listOf(cut, other), limit = 4, holding = listOf(other))
        assertEquals(6 + 290, result.more)
    }

    @Test
    fun `切れた entry が別にあっても 開いているファイルを持つ entry が切れていなければ 減らさない`() {
        val cut = layoutEntry("**.kt", LayoutEntryKind.AnyFile, matchedFiles = (1..10).map { "dir/F$it.kt" })
            .copy(matchedCount = 300, truncated = true)
        // The file is in neither list for a reason the entries do not tell (made after the definition task) and only the uncut entry holds it.
        val uncut = layoutEntry("S.kt", LayoutEntryKind.File, matchedFiles = listOf("s/Other.kt"))
        val result = SiblingFiles.of(self, listOf(cut, uncut), limit = 4, holding = listOf(uncut))
        assertEquals(7 + 290, result.more)
    }

    @Test
    fun `定義の後にできたファイルは 切れた側と見分けがつかないので 切れた entry が持つなら 切れた側とみなして 1 引く`() {
        val cut = layoutEntry("**.kt", LayoutEntryKind.AnyFile, matchedFiles = (1..10).map { "dir/F$it.kt" })
            .copy(matchedCount = 300, truncated = true)
        val result = SiblingFiles.of(self, listOf(cut), limit = 4, holding = listOf(cut))
        assertEquals(6 + 290 - 1, result.more)
    }

    @Test
    fun `兄弟が 1 つも無ければ空`() {
        assertEquals(SiblingFiles(emptyList(), 0), siblings(listOf(self)))
        assertEquals(SiblingFiles(emptyList(), 0), SiblingFiles.of(self, emptyList()))
    }

    @Test
    fun `親ディレクトリとファイル名の切り出し`() {
        assertEquals("a/b", SiblingFiles.parentOf("a/b/C.kt"))
        assertEquals("", SiblingFiles.parentOf("C.kt"))
        assertEquals("C.kt", SiblingFiles.nameOf("a/b/C.kt"))
        assertEquals("C.kt", SiblingFiles.nameOf("C.kt"))
    }

    @Test
    fun `長いディレクトリは中ほどを省く`() {
        assertEquals(".", SiblingFiles.shortDirectory(""))
        assertEquals("src/main", SiblingFiles.shortDirectory("src/main"))
        val long = "src/main/kotlin/com/example/application/service/internal"
        assertEquals("src/…/service/internal", SiblingFiles.shortDirectory(long, max = 30))
        assertEquals("src/…/application/service/internal", SiblingFiles.shortDirectory(long, max = 40))
        assertEquals(long, SiblingFiles.shortDirectory(long, max = 100))
    }

    @Test
    fun `省いても最後の 1 階層は残る`() {
        assertEquals("a/…/${"x".repeat(60)}", SiblingFiles.shortDirectory("a/b/${"x".repeat(60)}", max = 10))
    }
}
