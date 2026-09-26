package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.testing.ContractFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

class TemplateOutputParserTest {
    private val root = Paths.get("/tmp/katachi root")
    private val data = root.resolve("data/src/main/kotlin/com/example/data")

    private fun parse(name: String) = parseTemplateOutput(ContractFixtures.outputLines(name, root))

    @Test
    fun `新規に書いたファイルとプロジェクトルートを読む`() {
        val output = parse("new")
        assertEquals(root, output.projectRoot)
        assertEquals(listOf(data.resolve("UserRepository.kt"), data.resolve("UserRepositoryImpl.kt")), output.written)
        assertEquals(emptyList<Any>(), output.overwritten)
        assertTrue(output.reachedKatachi)
        assertEquals(TemplateRunStatus.Ok, output.status)
    }

    @Test
    fun `上書きしたファイルを書いたファイルの中から見分ける`() {
        val output = parse("overwrite")
        assertEquals(listOf(data.resolve("UserRepository.kt")), output.overwritten)
        assertEquals(2, output.written.size)
    }

    @Test
    fun `skipで書かなかった既存ファイルを末尾の括弧と句点を落として読む`() {
        val output = parse("skip")
        assertEquals(listOf(data.resolve("UserRepository.kt"), data.resolve("UserRepositoryImpl.kt")), output.skippedExisting)
        assertEquals(emptyList<Any>(), output.written)
    }

    @Test
    fun `衝突の本文から既存ファイルを読む`() {
        val status = parse("conflict").status as? TemplateRunStatus.Failed ?: throw AssertionError()
        assertEquals(listOf(data.resolve("UserRepository.kt")), status.conflicting)
    }

    @Test
    fun `衝突でない失敗は本文を字下げを外して持ち既存ファイルを持たない`() {
        val status = parse("slash-in-name").status as? TemplateRunStatus.Failed ?: throw AssertionError()
        assertNull(status.conflicting)
        assertTrue(status.body.first().startsWith("The template data/Repository would write"))
        assertEquals(2, status.body.size)
    }

    @Test
    fun `katachiまで届かなければまとめの行が無い`() {
        for (name in listOf("unknown-arg", "compile-failure")) {
            val output = parse(name)
            assertFalse(name, output.reachedKatachi)
            assertEquals(name, TemplateRunStatus.Missing, output.status)
        }
    }

    @Test
    fun `途中で切れた出力は改行の無い最後の行まで読んでまとめ行は無しにする`() {
        val output = parse("truncated")
        assertEquals(listOf(data.resolve("UserRepository.kt")), output.written)
        assertEquals(TemplateRunStatus.Missing, output.status)
    }

    @Test
    fun `ANSIの色コードが混ざっても読む`() {
        val output = parse("ansi")
        assertEquals(listOf(root.resolve("data/UserRepository.kt")), output.written)
        assertEquals(TemplateRunStatus.Ok, output.status)
    }

    @Test
    fun `空白と日本語のパーセントエンコードを戻しカンマ区切りで割れない`() {
        val output = parse("japanese-path")
        val dir = root.resolve("データ/My Module")
        assertEquals(listOf(dir.resolve("User Repository.kt"), dir.resolve("ユーザー.kt")), output.overwritten)
        assertEquals(output.overwritten, output.written)
    }

    @Test
    fun `templateキーを別のprocessorで上書きしていると書いた行が無いままOKになる`() {
        val output = parse("key-overridden")
        assertEquals(TemplateRunStatus.Ok, output.status)
        assertNull(output.projectRoot)
        assertEquals(emptyList<Any>(), output.written)
    }

    @Test
    fun `他のキーの行とまとめは読まない`() {
        val output = parseTemplateOutput(
            listOf("  [docs] Wrote file:///x/README.md", "[FAILED] docs", "  boom", "", "[OK] template"),
        )
        assertEquals(emptyList<Any>(), output.written)
        assertEquals(TemplateRunStatus.Ok, output.status)
    }

    @Test
    fun `file URIでない値は捨てる`() {
        assertNull(pathOfFileUri("https://example.com/a"))
        assertNull(pathOfFileUri("not a uri"))
        assertEquals(Paths.get("/a b/c"), pathOfFileUri("file:///a%20b/c"))
    }

    @Test
    fun `カンマと空白で割る`() {
        assertEquals(listOf("file:///a,b", "file:///c"), splitUriList("file:///a,b, file:///c"))
    }
}
