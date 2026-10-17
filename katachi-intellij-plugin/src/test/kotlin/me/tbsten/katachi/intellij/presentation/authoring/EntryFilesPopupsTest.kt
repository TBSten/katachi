package me.tbsten.katachi.intellij.presentation.authoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Paths

/** The popup of a layout line's Code Vision: the files it holds, or, for a line that holds none, its glob and what the directory really has. */
class EntryFilesPopupsTest {
    private val root = Paths.get("/work")

    private fun input(patterns: List<String>, files: List<String>, count: Int? = files.size) = EntryFilesInput(patterns, files, count, root)

    private val nothing: (String, Int) -> List<String> = { _, _ -> emptyList() }

    @Test
    fun `ファイルがあれば ファイル名の行を並べ 選ぶと開くファイルは プロジェクトの根からの絶対パス`() {
        val popup = EntryFilesPopups.of(input(listOf("core/*Service.kt"), listOf("core/A.kt", "core/B.kt")), nothing)

        assertEquals("core/*Service.kt", popup.title)
        assertEquals(listOf("A.kt", "B.kt"), popup.rows.map { it.text })
        assertEquals(listOf("core", "core"), popup.rows.map { it.trailing })
        assertEquals(listOf(root.resolve("core/A.kt"), root.resolve("core/B.kt")), popup.rows.map { it.target })
    }

    @Test
    fun `200 を超えたら 200 件で打ち切り 残りの数を 開けない行で言う`() {
        val files = List(250) { "d/F$it.kt" }

        val popup = EntryFilesPopups.of(input(listOf("d/*.kt"), files), nothing)

        assertEquals(ENTRY_POPUP_FILE_LIMIT + 1, popup.rows.size)
        assertEquals("… and 50 more (open the tool window)", popup.rows.last().text)
        assertNull(popup.rows.last().target)
    }

    @Test
    fun `JSON が一覧を切っていたら 数は切る前のもので 残りを数える`() {
        val popup = EntryFilesPopups.of(input(listOf("d/*.kt"), listOf("d/A.kt", "d/B.kt"), count = 900), nothing)

        assertEquals("… and 898 more (open the tool window)", popup.rows.last().text)
    }

    @Test
    fun `件数が分からない行は 検査がまだと言う`() {
        val popup = EntryFilesPopups.of(input(listOf("d/*.kt"), emptyList(), count = null), nothing)

        assertEquals(listOf("— File count unknown (the check has not run)"), popup.rows.map { it.text })
    }

    @Test
    fun `0 件の行は glob を言い その directory に実際にあるファイルを並べる`() {
        val asked = mutableListOf<Pair<String, Int>>()

        val popup = EntryFilesPopups.of(input(listOf("src/main/kotlin/com/example/*Servise.kt"), emptyList())) { directory, limit ->
            asked += directory to limit
            listOf("HealthService.kt", "PingService.kt")
        }

        assertEquals(listOf("src/main/kotlin/com/example" to ENTRY_POPUP_DIRECTORY_LIMIT + 1), asked)
        assertEquals(
            listOf("— No file matches src/main/kotlin/com/example/*Servise.kt", "— Files in src/main/kotlin/com/example:", "HealthService.kt", "PingService.kt"),
            popup.rows.map { it.text },
        )
        assertEquals(root.resolve("src/main/kotlin/com/example/HealthService.kt"), popup.rows[2].target)
    }

    @Test
    fun `directory のファイルは 50 個までで 超えたら 続きがあると言う`() {
        val popup = EntryFilesPopups.of(input(listOf("d/*.kt"), emptyList())) { _, limit -> List(limit) { "F$it.kt" } }

        assertEquals("… and more", popup.rows.last().text)
        assertEquals(ENTRY_POPUP_DIRECTORY_LIMIT, popup.rows.count { it.target != null })
    }

    @Test
    fun `directory が空か無いときは そう言う`() {
        val popup = EntryFilesPopups.of(input(listOf("missing/*.kt"), emptyList()), nothing)

        assertEquals(listOf("— No file matches missing/*.kt", "— There is no file in missing"), popup.rows.map { it.text })
    }

    @Test
    fun `wildcard の module key で glob が複数なら glob ごとに見出しと一覧が並ぶ`() {
        val popup = EntryFilesPopups.of(input(listOf("a/*.kt", "b/*.kt"), emptyList())) { directory, _ -> listOf("From${directory.uppercase()}.kt") }

        assertEquals("a/*.kt, b/*.kt", popup.title)
        assertEquals(
            listOf("— No file matches a/*.kt", "— Files in a:", "FromA.kt", "— No file matches b/*.kt", "— Files in b:", "FromB.kt"),
            popup.rows.map { it.text },
        )
    }

    @Test
    fun `glob の directory は ワイルドカードを含まない手前の段までで 最後の段は含まない`() {
        assertEquals("a/b", EntryFilesPopups.directoryOf("a/b/*Service.kt"))
        assertEquals("a/b", EntryFilesPopups.directoryOf("a/b/Fixed.kt"))
        assertEquals("a", EntryFilesPopups.directoryOf("a/*/c/*.kt"))
        assertEquals("", EntryFilesPopups.directoryOf("**/*.kt"))
        assertEquals("", EntryFilesPopups.directoryOf("settings.gradle.kts"))
        assertEquals("a", EntryFilesPopups.directoryOf("a/{x,y}/b.kt"))
    }
}
