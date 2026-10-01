package me.tbsten.katachi.intellij

import me.tbsten.katachi.intellij.preview.PreviewChecks
import me.tbsten.katachi.intellij.preview.PreviewChecks.DrawnText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The font gate of the preview ([PreviewChecks.fontProblems]); pure JVM like the other gates. */
class PreviewFontGateTest {

    /** Stands in for the bundled fonts: ASCII and the characters of 生成. */
    private val covers: (Int) -> Boolean = { it <= 0x7E || it == '生'.code || it == '成'.code }

    @Test
    fun `同梱の字体の文字だけを同梱の family で描いた文字列は問題なし`() {
        assertEquals(emptyList<String>(), PreviewChecks.fontProblems(listOf(DrawnText("生成 Generate", "FontFamily.SansSerif", bundledFamily = true)), covers))
    }

    @Test
    fun `同梱の字体に無い文字をコードポイントつきで挙げる`() {
        val found = PreviewChecks.fontProblems(listOf(DrawnText("生成を止める", "FontFamily.SansSerif", bundledFamily = true)), covers)

        assertEquals(1, found.size)
        assertTrue(found.single(), found.single().contains("'を' U+3092"))
        assertTrue(found.single(), found.single().contains("'止' U+6B62"))
        assertTrue(found.single(), found.single().contains("build_font.sh"))
    }

    @Test
    fun `同梱しない family で描いた文字列を検出する`() {
        val found = PreviewChecks.fontProblems(listOf(DrawnText("Generate", "FontListFontFamily(Inter)", bundledFamily = false)), covers)

        assertEquals(1, found.size)
        assertTrue(found.single(), found.single().startsWith("text:Generate is laid out in FontListFontFamily(Inter)"))
    }

    @Test
    fun `同じ文字列が何度描かれても1回だけ挙げる`() {
        val text = DrawnText("止", "FontFamily.SansSerif", bundledFamily = true)

        assertEquals(1, PreviewChecks.fontProblems(listOf(text, text), covers).size)
    }
}
