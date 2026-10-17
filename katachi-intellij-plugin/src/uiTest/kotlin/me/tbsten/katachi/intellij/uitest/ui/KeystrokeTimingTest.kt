package me.tbsten.katachi.intellij.uitest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.ScreenPhase
import me.tbsten.katachi.intellij.presentation.applyFormIntent
import me.tbsten.katachi.intellij.presentation.uiStateOf
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import me.tbsten.katachi.intellij.uitest.pbt.BranchKind
import me.tbsten.katachi.intellij.uitest.pbt.ParamSpec
import me.tbsten.katachi.intellij.uitest.pbt.ParamType
import me.tbsten.katachi.intellij.uitest.pbt.SetSpec
import me.tbsten.katachi.intellij.uitest.pbt.TemplateSpec
import me.tbsten.katachi.intellij.uitest.pbt.TitleKind
import me.tbsten.katachi.intellij.uitest.pbt.setRowOf
import me.tbsten.katachi.intellij.uitest.pbt.templateOf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Time one keystroke in the search field takes with 200 rows on screen (ide-review-screens T-12: every row is made again on
 * each key, `ListBody.kt`), with templates only and with some of the 200 template sets. The medians are printed for the
 * progress notes ([t12] lines); the only bound is a loose one, since the machine's speed is not under test.
 */
@OptIn(ExperimentalTestApi::class)
class KeystrokeTimingTest {
    private val arch = module(":arch-a")

    private class Host(initial: KatachiScreenState) {
        var state by mutableStateOf(initial)

        fun send(intent: KatachiIntent) {
            applyFormIntent(state, intent)?.let { state = it }
        }
    }

    private fun rows(templates: Int, sets: Int): List<TemplateModel> =
        (0 until templates).map { i ->
            templateOf(i, TemplateSpec("g${i % 10}", listOf(ParamSpec("name", ParamType.Str, false)), BranchKind.None, 1, false, false, TitleKind.None))
        } + (0 until sets).map { i ->
            setRowOf(i, SetSpec("g${i % 10}", listOf(ParamSpec("feature", ParamType.Str, false)), 2, false, TitleKind.None))
        }

    private fun ComposeUiTest.medianKeyMillis(rows: List<TemplateModel>): Pair<Double, Double> {
        val host = Host(
            KatachiScreenState(
                phase = ScreenPhase.Ready,
                modules = listOf(arch),
                snapshots = listOf(DescriptionSnapshot(arch, "0.3.0", rows, ScenarioHarness.NOW)),
            ),
        )
        setContent {
            IntUiTheme {
                Box(Modifier.requiredSize(420.dp, 720.dp).background(JewelTheme.globalColors.panelBackground)) {
                    KatachiToolWindowContent(uiStateOf(host.state, JapaneseKatachiStrings, ScenarioHarness.NOW), host::send)
                }
            }
        }
        waitForIdle()
        val search = onNodeWithTag(KatachiTestTags.SEARCH)
        val samples = mutableListOf<Long>()
        repeat(40) { round ->
            val started = System.nanoTime()
            search.performTextInput("R")
            waitForIdle()
            val typed = System.nanoTime() - started
            search.performTextClearance()
            waitForIdle()
            // The first rounds warm the JIT up.
            if (round >= 10) samples += typed
        }
        val sorted = samples.sorted().map { it / 1_000_000.0 }
        return sorted[sorted.size / 2] to sorted[sorted.size * 9 / 10]
    }

    @Test
    fun `検索欄の1キーにかかる時間を200行のテンプレートだけと組を混ぜた200行で測る`() = runComposeUiTest {
        val (plainMedian, plainP90) = medianKeyMillis(rows(templates = 200, sets = 0))
        println("[t12] 200 templates: median ${"%.1f".format(plainMedian)} ms, p90 ${"%.1f".format(plainP90)} ms per key")
        val (mixedMedian, mixedP90) = medianKeyMillis(rows(templates = 160, sets = 40))
        println("[t12] 160 templates + 40 sets: median ${"%.1f".format(mixedMedian)} ms, p90 ${"%.1f".format(mixedP90)} ms per key")
        assertTrue("a key takes ${"%.0f".format(mixedMedian)} ms with sets (${"%.0f".format(plainMedian)} ms without)", mixedMedian < 1_000)
    }
}
