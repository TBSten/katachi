package me.tbsten.katachi.intellij.preview

import java.io.File
import javax.imageio.ImageIO

/**
 * The mechanical gates on the preview output.
 *
 * Pure JVM on purpose: `test` reaches it through `sourceSets["preview"].output`, and importing
 * Compose here would load standalone Compose next to the IDE's bundled one.
 */
object PreviewChecks {

    /** Outputs this harness owns. Clean and golden sync touch nothing else. */
    private val managedPng = Regex("""preview-.*\.png""")

    fun isManagedPng(name: String): Boolean = managedPng.matches(name)

    /**
     * Deletes the managed outputs (preview-*.png, index.html, report/) before rendering, so that a
     * renamed or removed scenario does not linger in the gallery or the golden.
     */
    fun cleanManagedOutputs(dir: File) {
        dir.listFiles()?.forEach { f ->
            if (f.isFile && (isManagedPng(f.name) || f.name == "index.html")) f.delete()
        }
        File(dir, "report").deleteRecursively()
    }

    /** Compares the rendered PNGs with the expected names. Returns readable problems; empty means OK. */
    fun unexpectedFileSet(dir: File, expected: Set<String>): List<String> {
        val actual = dir.listFiles()
            ?.filter { it.isFile && isManagedPng(it.name) }
            ?.map { it.name }?.toSet().orEmpty()
        return buildList {
            (actual - expected).sorted().forEach { add("rendered a PNG no scenario expects: $it") }
            (expected - actual).sorted().forEach { add("an expected PNG was not rendered: $it") }
        }
    }

    /**
     * Returns the PNGs whose corner pixels are not fully opaque.
     *
     * A render root that does not paint the theme surface yields a transparent PNG, on which dark
     * markers and thin lines vanish in a dark viewer.
     */
    fun transparentCornerPngs(pngs: List<File>): List<File> = pngs.filter { file ->
        if (!file.isFile) return@filter false // a missing file is reported by unexpectedFileSet
        val img = ImageIO.read(file) ?: return@filter true // an undecodable file fails too
        val xs = intArrayOf(0, img.width - 1)
        val ys = intArrayOf(0, img.height - 1)
        xs.any { x -> ys.any { y -> (img.getRGB(x, y) ushr 24) != 0xFF } }
    }

    /**
     * One part of a rendered dialog, in pixels of the image. [scrolled] parts sit in the scrolling
     * form and may run past the bottom edge (they scroll into view); [textCut] says the text of the
     * part did not fit (an ellipsis or an overflow). An [overlay] (an opened select box) is drawn over
     * the rest by design, so it only has to fit the window.
     */
    data class LayoutNode(
        val name: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val scrolled: Boolean = false,
        val textCut: Boolean = false,
        val overlay: Boolean = false,
        /** What the cut text measured (size, lines, overflow), shown in the failure; empty when unknown. */
        val cutDetail: String = "",
    ) {
        fun overlaps(other: LayoutNode): Boolean = left < other.right && other.left < right && top < other.bottom && other.top < bottom

        fun contains(other: LayoutNode): Boolean = left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
    }

    /**
     * The overflow and cut-off gate of a rendered dialog of [width] x [height]. Returns readable
     * problems; empty means OK:
     * - a part sticks out of the window on the left, right or top, or below it unless it [scrolled]
     * - a part has no size
     * - two parts overlap (a part inside another is fine; a scrolled part is only compared with the
     *   scrolled ones, as it may be scrolled out from under the fixed parts, whose own box is the form)
     * - a text was cut
     */
    fun layoutProblems(width: Int, height: Int, nodes: List<LayoutNode>): List<String> = buildList {
        for (n in nodes) {
            if (n.right <= n.left || n.bottom <= n.top) add("${n.name} has no size")
            if (n.left < 0 || n.right > width) add("${n.name} sticks out of the window horizontally (${n.left}..${n.right} of $width)")
            if (n.top < 0) add("${n.name} sticks out above the window (top ${n.top})")
            if (!n.scrolled && n.bottom > height) add("${n.name} sticks out below the window (bottom ${n.bottom} of $height)")
            if (n.textCut) add("${n.name} is cut" + if (n.cutDetail.isEmpty()) "" else " (${n.cutDetail})")
        }
        for (i in nodes.indices) for (j in i + 1 until nodes.size) {
            val a = nodes[i]
            val b = nodes[j]
            if (!a.overlay && !b.overlay && a.scrolled == b.scrolled && a.overlaps(b) && !a.contains(b) && !b.contains(a)) add("${a.name} overlaps ${b.name}")
        }
    }

    /**
     * One text a preview drew: what it says, the font family it was laid out in, and whether that
     * family is one the preview fills with its bundled fonts (PreviewFonts).
     */
    data class DrawnText(val text: String, val fontFamily: String, val bundledFamily: Boolean)

    /**
     * The font gate: the preview draws with bundled fonts only, so that a PNG does not depend on the
     * fonts of the OS it was drawn on. Returns readable problems; empty means OK:
     * - a text is laid out in a family the preview does not fill (Skia would draw it with the OS fonts)
     * - a text has a character beyond ASCII that the bundled fonts lack, by [covers] (Skia would fall
     *   back to an OS font for it)
     */
    fun fontProblems(texts: List<DrawnText>, covers: (codePoint: Int) -> Boolean): List<String> = buildList {
        for (t in texts.distinct()) {
            if (!t.bundledFamily) add("text:${t.text} is laid out in ${t.fontFamily}, which the preview does not bundle; use the theme's text style or FontFamily.Monospace")
            val missing = t.text.codePoints().toArray().filter { it > 0x7E && !covers(it) }.distinct()
            if (missing.isNotEmpty()) {
                val chars = missing.joinToString { "'${String(Character.toChars(it))}' U+%04X".format(it) }
                add("text:${t.text} has characters the bundled preview fonts lack ($chars); run scripts/preview-font/build_font.sh")
            }
        }
    }

    /**
     * True when something is drawn on the outermost pixel ring of [png] (it differs from the corner):
     * a part that reaches the window edge was cut there. The dialog keeps a margin all around.
     */
    fun edgeTouched(png: File): Boolean {
        val img = ImageIO.read(png) ?: return true
        val corner = img.getRGB(0, 0)
        val last = img.width - 1
        val bottom = img.height - 1
        return (0..last).any { img.getRGB(it, 0) != corner || img.getRGB(it, bottom) != corner } ||
            (0..bottom).any { img.getRGB(0, it) != corner || img.getRGB(last, it) != corner }
    }

    /** The result of verify: changed = bytes differ, new = not in the golden, missing = only in the golden. */
    data class GoldenDiff(
        val changed: List<String>,
        val new: List<String>,
        val missing: List<String>,
    ) {
        fun isEmpty(): Boolean = changed.isEmpty() && new.isEmpty() && missing.isEmpty()
    }

    /**
     * verify: compares the rendered PNGs with the golden without touching it. A byte comparison,
     * which relies on rendering being byte-deterministic on the same machine.
     */
    fun diffAgainstGolden(outDir: File, goldenDir: File, expected: Set<String>): GoldenDiff {
        val goldenNames = goldenDir.listFiles()
            ?.filter { it.isFile && isManagedPng(it.name) }
            ?.map { it.name }?.toSet().orEmpty()
        val changed = expected.filter { name ->
            val golden = File(goldenDir, name)
            val actual = File(outDir, name)
            golden.isFile && actual.isFile && !golden.readBytes().contentEquals(actual.readBytes())
        }.sorted()
        return GoldenDiff(
            changed = changed,
            new = (expected - goldenNames).sorted(),
            missing = (goldenNames - expected).sorted(),
        )
    }

    /** update: force-syncs the golden, deleting stale PNGs. Files it does not own (.gitkeep) stay. */
    fun syncGolden(outDir: File, goldenDir: File, expected: Set<String>) {
        goldenDir.mkdirs()
        goldenDir.listFiles()?.forEach { f ->
            if (f.isFile && isManagedPng(f.name) && f.name !in expected) f.delete()
        }
        expected.sorted().forEach { name ->
            File(outDir, name).copyTo(File(goldenDir, name), overwrite = true)
        }
    }
}
