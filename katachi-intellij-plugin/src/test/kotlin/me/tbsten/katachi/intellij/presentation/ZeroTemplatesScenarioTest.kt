package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.cast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reload that leaves no template while a result is shown or a generation waits for it. Played on
 * a [ManualDispatcher], so every state between two resumptions can be looked at, not only the last.
 */
class ZeroTemplatesScenarioTest {
    private val base = ContractFixtures.json("arch-a")
    private val noTemplates = """{"templates": [], "details": []}"""

    @Test
    fun `結果を出したあとの再読み込みでテンプレートが0件になると結果を捨て一覧が戻っても古い結果を出さない`() = manual { dispatcher, s ->
        s.dispatch(KatachiIntent.Opened)
        dispatcher.runAll()
        s.check(s.noArgs)
        s.dispatch(KatachiIntent.Generate)
        dispatcher.runAll()
        s.state.generation.cast<GenerationState.Finished>()

        s.loadJson = noTemplates
        s.dispatch(KatachiIntent.Reload)
        dispatcher.runAll()
        assertEquals(ScreenPhase.Empty(EmptyReason.NoTemplates), s.state.phase)
        assertNull(s.state.generation)

        s.loadJson = base
        s.dispatch(KatachiIntent.Reload)
        dispatcher.runAll()
        assertEquals(ScreenPhase.Ready, s.state.phase)
        assertNull("the result of before the empty list came back", s.state.generation)
        assertTrue(s.ui().footer is FooterUi.Form)
    }

    @Test
    fun `読み込みを待つ生成の間にその読み込みでテンプレートが0件になると生成は一覧と一緒に消え何も生成しない`() = manual { dispatcher, s ->
        s.dispatch(KatachiIntent.Opened)
        dispatcher.runAll()
        s.check(s.noArgs)
        val gate = CompletableDeferred<Unit>()
        s.loadGate = gate
        s.loadJson = noTemplates
        s.dispatch(KatachiIntent.Reload)
        dispatcher.runAll()
        s.dispatch(KatachiIntent.Generate)
        dispatcher.runAll()
        assertTrue(s.state.generation.cast<GenerationState.Running>().waitingForLoad)

        // Every state between the load ending and the generation's job resuming, not only the last one.
        val outsideTheList = mutableListOf<String>()
        dispatcher.afterTask = {
            val state = s.state
            if (state.generation != null && state.phase != ScreenPhase.Ready) outsideTheList += "${state.generation} on ${state.phase}"
        }
        gate.complete(Unit)
        dispatcher.runAll()

        assertEquals(emptyList<String>(), outsideTheList)
        assertEquals(ScreenPhase.Empty(EmptyReason.NoTemplates), s.state.phase)
        assertNull(s.state.generation)
        assertEquals(emptyList<Map<String, String>>(), s.katachi.runs.toList())
    }

    private fun manual(body: (ManualDispatcher, ScenarioHarness) -> Unit) {
        val dispatcher = ManualDispatcher()
        val scope = CoroutineScope(dispatcher + Job())
        try {
            body(dispatcher, ScenarioHarness(scope, ioDispatcher = dispatcher))
        } finally {
            scope.cancel()
        }
    }
}
