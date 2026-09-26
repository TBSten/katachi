package me.tbsten.katachi.intellij.data.gradle

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputLineSplitterTest {
    private fun split(vararg chunks: String, flush: Boolean = true): List<String> {
        val lines = mutableListOf<String>()
        val splitter = OutputLineSplitter { lines += it }
        chunks.forEach(splitter::append)
        if (flush) splitter.flush()
        return lines
    }

    @Test
    fun `チャンクの途中で切れた行をつなげる`() {
        assertEquals(listOf("[OK] template", "done"), split("[OK] tem", "plate\ndo", "ne\n"))
    }

    @Test
    fun `CRLFとCRだけの改行も1行として扱う`() {
        assertEquals(listOf("a", "b", "c"), split("a\r", "\nb\rc\n"))
    }

    @Test
    fun `空行を落とさない`() {
        assertEquals(listOf("[FAILED] template", "  body", ""), split("[FAILED] template\n  body\n\n"))
    }

    @Test
    fun `改行で終わらない最後の行はflushで1行として渡す`() {
        assertEquals(emptyList<String>(), split("[OK] template", flush = false))
        assertEquals(listOf("[OK] template"), split("[OK] template"))
    }

    @Test
    fun `ANSIの色とカーソル移動を落とす`() {
        assertEquals("[OK] template", stripAnsi("\u001B[32m[OK]\u001B[0m template\u001B[2K"))
    }

    @Test
    fun `Gradle のタスク開始行からタスクのパスを取り出し、それ以外の行は null にする`() {
        assertEquals(":arch-a:compileTestKotlin", taskPathOf("> Task :arch-a:compileTestKotlin UP-TO-DATE"))
        assertEquals(":arch-a:katachiTemplate", taskPathOf("> Task :arch-a:katachiTemplate"))
        assertEquals(null, taskPathOf("  [template] Wrote file:///work/A.kt"))
        assertEquals(null, taskPathOf("> Configure project :arch-a"))
    }
}
