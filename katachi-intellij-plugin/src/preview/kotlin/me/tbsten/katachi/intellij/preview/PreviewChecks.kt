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
