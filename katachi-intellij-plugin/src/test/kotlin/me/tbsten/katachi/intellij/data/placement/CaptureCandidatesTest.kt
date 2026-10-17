package me.tbsten.katachi.intellij.data.placement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** [CaptureCandidates] (design ide-review-screens 3.2): the directory names at a capture's place in the pattern. */
class CaptureCandidatesTest {
    private val kmpPattern = "feature/\${feature}/src/commonMain/kotlin/com/example/kmp/feature/<feature>/component/<feature>\${name}.kt"
    private val jvmPattern = "src/main/kotlin/com/example/controller/\${resource}/\${name}Controller.kt"
    private val androidPattern = "feature/\${feature}/src/main/kotlin/com/example/sample/feature/<feature>/component/<feature>\${name}.kt"

    private fun pattern(text: String): PathPattern = PathPattern.parse(text) ?: throw AssertionError("`$text` is left out of the index")

    /** The sample's real tree (`contract/gutter/sources/<sample>/`), read as the real lister reads the disk. */
    private fun sampleRoot(sample: String): Path =
        Paths.get(javaClass.getResource("/contract/gutter/sources/$sample")?.toURI() ?: throw AssertionError("No fixture tree: $sample"))

    private val diskLister = DirectoryLister { directory ->
        if (!Files.isDirectory(directory)) emptyList() else Files.list(directory).use { s -> s.filter(Files::isDirectory).map { it.fileName.toString() }.toList() }
    }

    private fun candidates(
        pattern: String,
        capture: String,
        entered: Map<String, String> = emptyMap(),
        root: Path = Paths.get("/work"),
        lister: DirectoryLister = DirectoryLister { emptyList() },
    ) = CaptureCandidates.of(pattern(pattern), capture, entered, root, lister)

    /** A tree in memory: [children] by directory (relative to [root]). A directory not listed has no children. */
    private fun treeOf(root: Path, children: Map<String, List<String>>) =
        DirectoryLister { directory -> children[root.relativize(directory).joinToString("/")].orEmpty() }

    @Test
    fun `kmp の feature の欄には 実際にあるモジュールのディレクトリ名が出る`() {
        val found = candidates(kmpPattern, "feature", root = sampleRoot("kmp"), lister = diskLister)
        assertEquals(CaptureCandidateValues(listOf("home", "settings"), 0), found)
    }

    @Test
    fun `jvm の resource の欄には controller の下のディレクトリ名が出る`() {
        val found = candidates(jvmPattern, "resource", root = sampleRoot("jvm"), lister = diskLister)
        assertEquals(CaptureCandidateValues(listOf("health"), 0), found)
    }

    @Test
    fun `android の feature の欄にも実際にあるモジュールのディレクトリ名が出る`() {
        val found = candidates(androidPattern, "feature", root = sampleRoot("android"), lister = diskLister)
        assertEquals(listOf("home", "settings"), found?.values)
    }

    @Test
    fun `同じ capture が2か所にあるときは外側の1か所だけを使う`() {
        val root = Paths.get("/work")
        val lister = treeOf(root, mapOf("a" to listOf("p"), "a/p/b" to listOf("q")))
        assertEquals(listOf("p"), candidates("a/\${x}/b/\${x}/f.kt", "x", root = root, lister = lister)?.values)
    }

    @Test
    fun `派生の2回目の出現は capture として数えない`() {
        val root = Paths.get("/work")
        val lister = treeOf(root, mapOf("feature" to listOf("home"), "feature/home/src/commonMain/kotlin/com/example/kmp/feature" to listOf("other")))
        assertEquals(listOf("home"), candidates(kmpPattern, "feature", root = root, lister = lister)?.values)
    }

    @Test
    fun `手前の階層の capture が未入力なら候補は無い`() {
        val root = Paths.get("/work")
        val lister = treeOf(root, mapOf("a/b" to listOf("x")))
        assertNull(candidates("a/\${p}/\${q}/f.kt", "q", root = root, lister = lister))
        assertNull(candidates("a/\${p}/\${q}/f.kt", "q", entered = mapOf("p" to " "), root = root, lister = lister))
        assertEquals(listOf("x"), candidates("a/\${p}/\${q}/f.kt", "q", entered = mapOf("p" to "b"), root = root, lister = lister)?.values)
    }

    @Test
    fun `手前の階層の派生は入力済みの値を変換して使う`() {
        val root = Paths.get("/work")
        val lister = treeOf(root, mapOf("MyPage" to listOf("x")))
        val text = "<p.pascalCase>/\${q}/f.kt"
        assertEquals(listOf("x"), candidates(text, "q", entered = mapOf("p" to "my-page"), root = root, lister = lister)?.values)
        assertNull(candidates("<p>/\${q}/f.kt", "q", entered = mapOf("p" to "my-page"), root = root, lister = lister))
    }

    /** A lister that records the directories it was asked for. */
    private class Recording(private val tree: Map<String, List<String>>, private val root: Path) : DirectoryLister {
        val asked = mutableListOf<Path>()
        override fun childDirectories(directory: Path): List<String> {
            asked.add(directory)
            return tree[root.relativize(directory).joinToString("/")].orEmpty()
        }
    }

    @Test
    fun `glob の文字を含む階層では候補が無く ディスクも読まない`() {
        val root = Paths.get("/work")
        val recording = Recording(mapOf("" to listOf("a"), "a{1,2}" to listOf("x")), root)
        assertNull(candidates("a{1,2}/\${q}/f.kt", "q", root = root, lister = recording))
        assertTrue("read the disk: ${recording.asked}", recording.asked.isEmpty())
    }

    @Test
    fun `区切り 親ディレクトリ 空白を含む値では候補が無く root の外を読まない`() {
        val root = Paths.get("/work")
        for (bad in listOf("a/b", "a\\b", "..", ".", " ")) {
            val recording = Recording(mapOf("a" to listOf("x"), "a/b" to listOf("x")), root)
            assertNull("`$bad`", candidates("\${p}/\${q}/f.kt", "q", entered = mapOf("p" to bad), root = root, lister = recording))
            assertTrue("`$bad` read the disk: ${recording.asked}", recording.asked.isEmpty())
        }
    }

    @Test
    fun `ファイル名の階層の capture には候補が無い`() {
        val root = Paths.get("/work")
        assertNull(candidates("a/\${name}.kt", "name", root = root, lister = treeOf(root, mapOf("a" to listOf("x")))))
        assertNull(candidates("a/\${q}/f.kt", "unknown", root = root, lister = treeOf(root, mapOf("a" to listOf("x")))))
    }

    @Test
    fun `混ざった階層は capture の部分だけが候補になり 合わないディレクトリは出ない`() {
        val root = Paths.get("/work")
        val lister = treeOf(root, mapOf("mods" to listOf("feature-home", "feature-settings", "core", "feature-")))
        assertEquals(listOf("home", "settings"), candidates("mods/feature-\${f}/f.kt", "f", root = root, lister = lister)?.values)
    }

    @Test
    fun `katachi が拒む値になるディレクトリ名は出ない`() {
        val root = Paths.get("/work")
        val lister = treeOf(root, mapOf("mods" to listOf("ok", "trailing.", "CON", "a b ")))
        assertEquals(listOf("ok"), candidates("mods/\${f}/f.kt", "f", root = root, lister = lister)?.values)
    }

    @Test
    fun `ドットで始まる名前は外し 大文字小文字を無視した辞書順に並べる`() {
        val root = Paths.get("/work")
        val lister = treeOf(root, mapOf("mods" to listOf("beta", ".git", "Alpha", "alpha2", "Charlie", ".idea")))
        assertEquals(listOf("Alpha", "alpha2", "beta", "Charlie"), candidates("mods/\${f}/f.kt", "f", root = root, lister = lister)?.values)
    }

    @Test
    fun `101件あるときは先頭100件とあふれた件数を返す`() {
        val root = Paths.get("/work")
        val names = (1..101).map { "m%03d".format(it) }
        val found = candidates("mods/\${f}/f.kt", "f", root = root, lister = treeOf(root, mapOf("mods" to names.reversed())))
        assertEquals(names.take(100), found?.values)
        assertEquals(1, found?.hiddenCount)
    }

    @Test
    fun `ちょうど100件ならあふれは無い`() {
        val root = Paths.get("/work")
        val names = (1..100).map { "m%03d".format(it) }
        assertEquals(0, candidates("mods/\${f}/f.kt", "f", root = root, lister = treeOf(root, mapOf("mods" to names)))?.hiddenCount)
    }

    @Test
    fun `ディレクトリが無い 空のとき候補は無い`() {
        val root = Paths.get("/work")
        assertNull(candidates("mods/\${f}/f.kt", "f", root = root))
    }

    @Test
    fun `読み込みの失敗は候補なしで 原因が通知される`() {
        val failure = IOException("denied")
        var reported: IOException? = null
        val found = CaptureCandidates.of(pattern("mods/\${f}/f.kt"), "f", emptyMap(), Paths.get("/work"), { throw failure }) { reported = it }
        assertNull(found)
        assertEquals(failure, reported)
    }

    @Test
    fun `IOException 以外の例外は握らない`() {
        val result = runCatching { CaptureCandidates.of(pattern("mods/\${f}/f.kt"), "f", emptyMap(), Paths.get("/work"), { throw IllegalStateException("boom") }) }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
