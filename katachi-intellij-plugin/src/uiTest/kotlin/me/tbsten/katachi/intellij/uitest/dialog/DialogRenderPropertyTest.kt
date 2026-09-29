package me.tbsten.katachi.intellij.uitest.dialog

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.toPropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.presentation.FieldUi
import me.tbsten.katachi.intellij.presentation.dialog.dialogUiStateOf
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogStrings
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogUiState
import me.tbsten.katachi.intellij.ui.dialog.PropertiesGenerateDialogStrings
import me.tbsten.katachi.intellij.uitest.pbt.DialogScenario
import me.tbsten.katachi.intellij.uitest.pbt.dialogScenarioArb
import me.tbsten.katachi.intellij.uitest.pbt.realWorldArb
import me.tbsten.katachi.intellij.uitest.pbt.syntheticWorldArb
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The generate dialog drawn after every step of a sequence of operations (see [DialogMachine]) at a
 * window size that changes along the way: no input leaves the window sideways or overlaps another,
 * and the selects, the form and the path stay in the window (see [layoutProblemsOf]). The window is
 * an operation too, as the real dialog is resizable; below [MIN_WIDTH] x [MIN_HEIGHT] the dialog
 * does not shrink (D4 sets that as its minimum size).
 *
 * A failure writes the shrunk sequence and the window sizes to `dialog-failure.txt`, and the
 * picture of the state it failed on to `dialog-failure.png`, under `katachi.pbt.outDir`
 * (`build/uiTest-failures`); the files are overwritten by each failing try, so the last is the shrunk one.
 * `-Pkatachi.pbt.seed=N` and `-Pkatachi.pbt.scale=N` work as in [DialogPropertyTest].
 */
class DialogRenderPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20260930L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val outDir = File(System.getProperty("katachi.pbt.outDir") ?: "build/uiTest-failures")

    private fun config(defaultIterations: Int) = PropTest(
        seed = seed,
        iterations = (defaultIterations * scale).toInt().coerceAtLeast(1),
        shrinkingMode = ShrinkingMode.Bounded(300),
    ).toPropTestConfig()

    private val reached = sortedMapOf<String, Int>()
    private var renders = 0

    /** Plays [scenario] and draws each settled state at the next of [sizes]; a problem is saved with its picture. */
    private fun play(scenario: DialogScenario, sizes: List<WindowSize>, dark: Boolean, strings: GenerateDialogStrings) {
        var step = 0
        var lastDrawn: Pair<GenerateDialogUiState, WindowSize>? = null
        val trace = mutableListOf<String>()
        val machine = DialogMachine(scenario) { name, view ->
            val size = sizes[step++ % sizes.size]
            val ui = dialogUiStateOf(view.state, strings)
            trace += "$name @ $size"
            if (lastDrawn == (ui to size)) return@DialogMachine
            lastDrawn = ui to size
            val frame = drawDialog(ui, strings, size, dark)
            renders++
            if (ui.fields.size >= 5) reached.merge("many fields", 1, Int::plus)
            if (ui.definitionOptions.isNotEmpty()) reached.merge("definition select", 1, Int::plus)
            if (size.width < 500) reached.merge("narrow", 1, Int::plus)
            if (size.height < 450) reached.merge("short", 1, Int::plus)
            val problems = layoutProblemsOf(frame, size, expectedFields = ui.fields.count { it !is FieldUi.Collapsed })
            if (problems.isNotEmpty()) {
                val report = buildString {
                    appendLine("after $name at $size:")
                    problems.forEach { appendLine("  - $it") }
                    appendLine("scenario:$scenario")
                    appendLine("window sizes: $sizes")
                    appendLine("steps: ${trace.joinToString(" ; ")}")
                }
                save(report, frame.png)
                throw AssertionError(report)
            }
        }
        machine.use {
            it.run()
            it.reached.forEach { (key, count) -> reached.merge(key, count, Int::plus) }
        }
    }

    private fun save(report: String, png: ByteArray) {
        runCatching {
            outDir.mkdirs()
            File(outDir, "dialog-failure.txt").writeText(report)
            File(outDir, "dialog-failure.png").writeBytes(png)
        }
    }

    private val windowSizeArb: Arb<WindowSize> = Arb.choose(
        1 to Arb.int(0..1).map { WindowSize(MIN_WIDTH, MIN_HEIGHT) },
        6 to arbitrary { WindowSize(Arb.int(MIN_WIDTH..1100).bind(), Arb.int(MIN_HEIGHT..900).bind()) },
        1 to arbitrary { WindowSize(Arb.int(MIN_WIDTH..500).bind(), Arb.int(MIN_HEIGHT..500).bind()) },
    )

    // covers: 論点1, 論点2
    @Test
    fun `どんな操作の列と窓の大きさでも入力欄は窓から横にはみ出さず互いに重ならず選択と経路の部品も重ならない`() = runBlocking {
        val strings = PropertiesGenerateDialogStrings.english()
        try {
            checkAll(config(defaultIterations = 15), dialogScenarioArb(syntheticWorldArb, 1..10), Arb.list(windowSizeArb, 1..11)) { scenario, sizes ->
                play(scenario, sizes, dark = false, strings)
            }
        } catch (e: Throwable) {
            outDir.mkdirs()
            File(outDir, "dialog-shrunk.txt").writeText(e.message.orEmpty())
            throw e
        }
        println("[pbt] dialog render synthetic: $renders renders, reached=$reached (seed=$seed)")
        for (key in listOf("many fields", "narrow", "short")) {
            assertTrue("never drew '$key': $reached", (reached[key] ?: 0) > 0)
        }
    }

    // covers: 論点1, 論点2
    @Test
    fun `日本語の文言と暗い配色でも同じく重ならずはみ出さない`() = runBlocking {
        val strings = PropertiesGenerateDialogStrings.japanese()
        checkAll(config(defaultIterations = 8), dialogScenarioArb(syntheticWorldArb, 1..8), Arb.list(windowSizeArb, 1..9)) { scenario, sizes ->
            play(scenario, sizes, dark = true, strings)
        }
        println("[pbt] dialog render japanese/dark: $renders renders (seed=$seed)")
    }

    // covers: 論点1, 論点2
    @Test
    fun `sample 3種の本物のJSONの定義でも窓の大きさを変えながら重ならずはみ出さない`() = runBlocking {
        val strings = PropertiesGenerateDialogStrings.english()
        checkAll(config(defaultIterations = 8), dialogScenarioArb(realWorldArb, 1..8), Arb.list(windowSizeArb, 1..9)) { scenario, sizes ->
            play(scenario, sizes, dark = false, strings)
        }
        assertTrue("never drew a definition select box: $reached", (reached["definition select"] ?: 0) > 0)
    }

    private companion object {
        const val MIN_WIDTH = 360
        const val MIN_HEIGHT = 320
    }
}
