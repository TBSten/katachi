package me.tbsten.katachi.intellij

import me.tbsten.katachi.intellij.preview.PreviewChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Tests the preview gates. PreviewChecks is pure JVM, so `sourceSets["preview"].output` is enough
 * and standalone Compose stays off the test classpath.
 */
class PreviewOutputGateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun writePng(dir: File, name: String, transparentCorner: Boolean): File {
        val img = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color(0x2B, 0x2D, 0x30)
        g.fillRect(0, 0, 8, 8)
        g.dispose()
        if (transparentCorner) img.setRGB(0, 0, 0x00000000)
        val file = File(dir, name)
        ImageIO.write(img, "png", file)
        return file
    }

    @Test
    fun `透明角のある PNG だけを検出する`() {
        val dir = tmp.newFolder()
        val opaque = writePng(dir, "preview-opaque-light.png", transparentCorner = false)
        val transparent = writePng(dir, "preview-transparent-dark.png", transparentCorner = true)

        val detected = PreviewChecks.transparentCornerPngs(listOf(opaque, transparent))

        assertEquals(listOf(transparent), detected)
    }

    @Test
    fun `expected filename set との不一致を両方向で検出する`() {
        val dir = tmp.newFolder()
        writePng(dir, "preview-default-light.png", transparentCorner = false)
        writePng(dir, "preview-stale-light.png", transparentCorner = false)

        val problems = PreviewChecks.unexpectedFileSet(
            dir,
            expected = setOf("preview-default-light.png", "preview-default-dark.png"),
        )

        assertEquals(2, problems.size)
        assertTrue(problems.any { it.contains("preview-stale-light.png") })
        assertTrue(problems.any { it.contains("preview-default-dark.png") })
    }

    @Test
    fun `golden との差分を changed と new と missing に分類する`() {
        val outDir = tmp.newFolder("out")
        val goldenDir = tmp.newFolder("golden")
        // same: identical in both / changed: contents differ / new: not in the golden / missing: only in the golden
        writePng(outDir, "preview-same-light.png", transparentCorner = false)
        File(goldenDir, "preview-same-light.png").writeBytes(File(outDir, "preview-same-light.png").readBytes())
        writePng(outDir, "preview-changed-light.png", transparentCorner = false)
        writePng(goldenDir, "preview-changed-light.png", transparentCorner = true)
        writePng(outDir, "preview-new-light.png", transparentCorner = false)
        writePng(goldenDir, "preview-missing-light.png", transparentCorner = false)

        val diff = PreviewChecks.diffAgainstGolden(
            outDir,
            goldenDir,
            expected = setOf("preview-same-light.png", "preview-changed-light.png", "preview-new-light.png"),
        )

        assertEquals(listOf("preview-changed-light.png"), diff.changed)
        assertEquals(listOf("preview-new-light.png"), diff.new)
        assertEquals(listOf("preview-missing-light.png"), diff.missing)
        assertTrue(!diff.isEmpty())
    }

    private fun writeShaded(dir: File, name: String, shift: Int): File {
        val img = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color(0x2B, 0x2D, 0x30)
        g.fillRect(0, 0, 8, 8)
        g.dispose()
        // One pixel of a glyph's edge, drawn a little lighter or darker.
        img.setRGB(3, 3, Color(0x80 + shift, 0x80 + shift, 0x80 + shift).rgb)
        return File(dir, name).also { ImageIO.write(img, "png", it) }
    }

    @Test
    fun `文字の縁の階調ほどの差は golden と同じとみなし、それより大きい差は changed にする`() {
        val outDir = tmp.newFolder("out")
        val goldenDir = tmp.newFolder("golden")
        writeShaded(goldenDir, "preview-shade-light.png", shift = 0)
        writeShaded(outDir, "preview-shade-light.png", shift = PreviewChecks.CHANNEL_TOLERANCE)
        writeShaded(goldenDir, "preview-moved-light.png", shift = 0)
        writeShaded(outDir, "preview-moved-light.png", shift = PreviewChecks.CHANNEL_TOLERANCE + 1)

        val diff = PreviewChecks.diffAgainstGolden(
            outDir,
            goldenDir,
            expected = setOf("preview-shade-light.png", "preview-moved-light.png"),
        )

        assertEquals(listOf("preview-moved-light.png"), diff.changed)
    }
}
