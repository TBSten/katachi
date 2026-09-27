package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.cast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generate pressed again right after cancelling, with the cancelled job's cleanup running only after
 * the new generation reached its build: an interleaving a thread pool hits by chance, played here
 * on one thread in a fixed order.
 */
class GenerationCancelRaceTest {
    @Test
    fun `キャンセルした生成の後始末が押し直した生成より遅れて走ってもそのセッションを消さずキャンセルは中断の結果になる`() {
        val dispatcher = ManualDispatcher()
        val scope = CoroutineScope(dispatcher + Job())
        try {
            val s = ScenarioHarness(scope, ioDispatcher = dispatcher)
            s.dispatch(KatachiIntent.Opened)
            dispatcher.runAll()
            s.check(s.repository)
            s.input(s.repository, "name", "User")

            // A refresh is running, so the first generation waits for it (E-41) before any session exists.
            val load = CompletableDeferred<Unit>()
            s.loadGate = load
            s.dispatch(KatachiIntent.Reload)
            dispatcher.runAll()
            s.dispatch(KatachiIntent.Generate)
            dispatcher.runAll()
            assertTrue(s.state.generation.cast<GenerationState.Running>().waitingForLoad)

            s.dispatch(KatachiIntent.CancelGeneration)
            // The first job's resumption, which runs its cleanup: held back until the second job is running.
            val firstJobCleanup = dispatcher.hold()
            assertEquals(1, firstJobCleanup.size)

            load.complete(Unit)
            dispatcher.runAll()
            val build = CompletableDeferred<Unit>()
            s.katachi.override = { _, _ -> FakeRun(gate = build) }
            s.dispatch(KatachiIntent.Generate)
            dispatcher.runAll()
            assertEquals(1, s.katachi.runs.size)

            dispatcher.run(firstJobCleanup)
            s.dispatch(KatachiIntent.CancelGeneration)
            dispatcher.runAll()

            // The second generation's session got the cancel: it ends on the result screen as interrupted,
            // rather than its job being torn down and the screen going back to the form.
            val finished = s.state.generation.cast<GenerationState.Finished>()
            assertTrue(finished.report.items.single().result is GenerationItemResult.Interrupted)
            assertEquals(1, s.runner.cancelled)
        } finally {
            scope.cancel()
        }
    }
}
