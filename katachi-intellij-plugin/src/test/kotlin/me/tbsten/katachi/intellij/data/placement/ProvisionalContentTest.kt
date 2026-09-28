package me.tbsten.katachi.intellij.data.placement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisionalContentTest {
    private val command = "./gradlew :a:katachiTemplate --arg template=x"

    private fun of(fileName: String, packageName: String? = null, notice: String = "Generating") =
        ProvisionalContent.of(fileName, command, notice, packageName)

    // covers: 論点16
    @Test
    fun `ktはpackage行と案内とコマンドのスラッシュ2本のコメントになる`() {
        assertEquals("package a.b\n\n// Generating\n// $command\n", of("Home.kt", "a.b"))
    }

    // covers: 論点16
    @Test
    fun `javaのpackage行はセミコロン付き`() {
        assertEquals("package a.b;\n\n// Generating\n// $command\n", of("Home.java", "a.b"))
    }

    // covers: 論点16
    @Test
    fun `ソースルートの外でパッケージ名が無いときはpackage行を書かない`() {
        assertEquals("// Generating\n// $command\n", of("Home.kt", null))
        assertEquals("// Generating\n// $command\n", of("Home.java", null))
    }

    // covers: 論点16
    @Test
    fun `ktsとgradleとpropertiesはパッケージ名があってもpackage行を書かない`() {
        assertEquals("// Generating\n// $command\n", of("a.kts", "a.b"))
        assertEquals("// Generating\n// $command\n", of("a.gradle", "a.b"))
        assertEquals("# Generating\n# $command\n", of("a.properties", "a.b"))
    }

    // covers: 論点16
    @Test
    fun `識別子の並びでないパッケージ名は書かない`() {
        for (bad in listOf("", "a..b", ".a", "a.", "1a", "a b", "a\nimport x", "a;b")) {
            assertEquals(bad, "// Generating\n// $command\n", of("A.kt", bad))
        }
    }

    // covers: 論点16
    @Test
    fun `Hash系はシャープ Html系はコメントの範囲で書く`() {
        assertEquals("# Generating\n# $command\n", of("a.yaml"))
        // `--arg` cannot appear inside an XML/HTML comment: its dashes get a space (the command is then not copy-pasteable there).
        assertEquals("<!-- Generating -->\n<!-- ./gradlew :a:katachiTemplate - -arg template=x -->\n", of("a.xml"))
    }

    // covers: 論点16
    @Test
    fun `jsonと知らない拡張子と拡張子無しは空のファイル`() {
        assertEquals("", of("a.json"))
        assertEquals("", of("a.unknown"))
        assertEquals("", of("Makefile"))
    }

    // covers: 論点16
    @Test
    fun `複数行の案内は各行にコメント記号が付き空行は記号だけ`() {
        val content = ProvisionalContent.of("A.kt", "cmd", "one\r\n\ntwo\rthree", null)
        assertEquals("// one\n//\n// two\n// three\n// cmd\n", content)
    }

    // covers: 論点16
    @Test
    fun `ブロックコメントを閉じる文字列とHtmlのハイフン2つは外に出ない`() {
        assertEquals("// x */ y\n// c\n", ProvisionalContent.of("A.kt", "c", "x */ y", null))
        val html = ProvisionalContent.of("a.md", "c", "a --> b --!> ---", null)
        assertEquals("<!-- a - -> b - -!> - - - -->\n<!-- c -->\n", html)
        assertTrue(EmptyFileRule.isEmpty(html, "a.md"))
    }

    // covers: 論点16
    @Test
    fun `JavaのUnicodeエスケープの改行でコメントの外に出ない`() {
        assertEquals("// a \\ u000a b\n// c\n", ProvisionalContent.of("A.java", "c", "a \\u000a b", null))
    }

    // covers: 論点16
    @Test
    fun `仮の中身は空の判定に当たる`() {
        for (name in listOf("A.kt", "A.java", "a.kts", "a.properties", "a.xml", "a.json")) {
            assertTrue(name, EmptyFileRule.isEmpty(of(name, "a.b"), name))
        }
    }

    // covers: 論点16
    @Test
    fun `書いたままなら仮の中身のままで1文字変えると違う`() {
        val written = of("A.kt", "a.b")
        assertTrue(ProvisionalContent.isStillProvisional(StringBuilder(written), written))
        assertFalse(ProvisionalContent.isStillProvisional(written + " ", written))
        assertFalse(ProvisionalContent.isStillProvisional(written.dropLast(1), written))
        assertFalse(ProvisionalContent.isStillProvisional(written.replaceFirst("Generating", "Generatinh"), written))
        assertFalse(ProvisionalContent.isStillProvisional("", written))
    }
}
