package me.tbsten.katachi.intellij

import me.tbsten.katachi.intellij.preview.PreviewChecks
import me.tbsten.katachi.intellij.preview.PreviewChecks.LayoutNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** The overflow and cut-off gate of the dialog preview ([PreviewChecks.layoutProblems]); pure JVM like the other gates. */
class PreviewLayoutGateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun problems(vararg nodes: LayoutNode) = PreviewChecks.layoutProblems(width = 200, height = 100, nodes.toList())

    private fun node(name: String, left: Int, top: Int, right: Int, bottom: Int, scrolled: Boolean = false, textCut: Boolean = false, overlay: Boolean = false) =
        LayoutNode(name, left, top, right, bottom, scrolled, textCut, overlay)

    @Test
    fun `窓の中に収まって重ならない部品は問題なし`() {
        assertEquals(emptyList<String>(), problems(node("a", 10, 10, 90, 30), node("b", 100, 10, 190, 30), node("c", 10, 40, 190, 60)))
    }

    @Test
    fun `左右にはみ出す部品と上にはみ出す部品を検出する`() {
        val found = problems(node("wide", 10, 10, 250, 30), node("left", -3, 40, 50, 60), node("up", 10, -2, 50, 20))

        assertTrue(found.any { it.startsWith("wide sticks out of the window horizontally") })
        assertTrue(found.any { it.startsWith("left sticks out of the window horizontally") })
        assertTrue(found.any { it.startsWith("up sticks out above the window") })
    }

    @Test
    fun `下にはみ出すのはスクロールする部品だけが許される`() {
        val found = problems(node("fixed", 10, 80, 90, 130), node("scrolled", 10, 80, 90, 300, scrolled = true))

        assertEquals(1, found.size)
        assertTrue(found.single().startsWith("fixed sticks out below the window"))
    }

    @Test
    fun `重なる部品を検出し、他の部品を含む部品と重ならない隣り合いは通す`() {
        val found = problems(node("outer", 0, 0, 200, 100), node("inner", 10, 10, 50, 30), node("x", 40, 10, 120, 30, scrolled = false), node("touching", 120, 10, 160, 30))

        assertEquals(listOf("inner overlaps x"), found)
    }

    @Test
    fun `スクロールの中の部品は固定の部品と比べず、スクロールの中同士で比べる`() {
        val fixedVsScrolled = problems(node("path", 10, 60, 190, 80), node("field", 10, 70, 190, 90, scrolled = true))
        val scrolledPair = problems(node("f1", 10, 10, 190, 40, scrolled = true), node("f2", 10, 30, 190, 60, scrolled = true))

        assertEquals(emptyList<String>(), fixedVsScrolled)
        assertEquals(listOf("f1 overlaps f2"), scrolledPair)
    }

    @Test
    fun `重ねて出す部品は窓に収まればよく、切れた文字は検出する`() {
        assertEquals(emptyList<String>(), problems(node("field", 10, 10, 190, 30), node("popup", 20, 20, 180, 90, overlay = true)))
        assertEquals(listOf("label is cut"), problems(node("label", 10, 10, 60, 30, textCut = true)))
    }

    @Test
    fun `大きさの無い部品を検出する`() {
        assertEquals(listOf("empty has no size"), problems(node("empty", 10, 10, 10, 30)))
    }

    @Test
    fun `窓の縁のちょうど外周に何か描かれていれば検出する`() {
        fun png(name: String, mark: Boolean): File {
            val img = BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.color = Color(0xF7, 0xF8, 0xFA)
            g.fillRect(0, 0, 20, 20)
            if (mark) {
                g.color = Color.BLACK
                g.fillRect(19, 5, 1, 4)
            }
            g.dispose()
            return File(tmp.root, name).also { ImageIO.write(img, "png", it) }
        }

        assertFalse(PreviewChecks.edgeTouched(png("clean.png", mark = false)))
        assertTrue(PreviewChecks.edgeTouched(png("touched.png", mark = true)))
    }
}
