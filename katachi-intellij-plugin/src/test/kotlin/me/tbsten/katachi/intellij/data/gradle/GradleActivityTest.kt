package me.tbsten.katachi.intellij.data.gradle

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

class GradleActivityTest {
    private val request = GradleRunRequest(Paths.get("/p"), listOf(GradleTaskInvocation(":arch:test")))

    @Test
    fun `自分のビルドの間だけ走っている`() = runBlocking {
        val activity = GradleActivity()
        val gate = CompletableDeferred<Unit>()
        val runner = FakeGradleTaskRunner { _, _ -> FakeRun(gate = gate) }
        val tracked = activity.track(runner)
        assertFalse(activity.busy.value)
        val run = async { tracked.run(request, object : GradleRunListener {}) }
        runner.reachedGate.await()
        assertTrue(activity.busy.value)
        gate.complete(Unit)
        run.await()
        assertFalse(activity.busy.value)
    }

    @Test
    fun `IDE のタスクは始まりと終わりで数え同じ id は1回`() {
        val activity = GradleActivity()
        val history = mutableListOf<Boolean>()
        activity.externalStarted("sync")
        history += activity.busy.value
        activity.externalStarted("sync")
        activity.externalStarted("build")
        activity.externalEnded("sync")
        history += activity.busy.value
        activity.externalEnded("build")
        history += activity.busy.value
        activity.externalEnded("never-started")
        history += activity.busy.value
        assertEquals(listOf(true, true, false, false), history)
    }

    @Test
    fun `待つと IDE のタスクが終わったときに戻る`() = runBlocking {
        val activity = GradleActivity()
        activity.externalStarted(1)
        val waiting = async { activity.awaitIdle() }
        assertFalse(waiting.isCompleted)
        activity.externalEnded(1)
        withTimeout(5_000) { waiting.await() }
    }

    @Test
    fun `直列の列に activity を渡すと順番待ちの間も、前の実行が終わった次の瞬間も走っていると数える`() = runBlocking {
        val activity = GradleActivity()
        val gate = CompletableDeferred<Unit>()
        val runner = FakeGradleTaskRunner { _, _ -> FakeRun(gate = gate) }
        val serial = SerialGradleTaskRunner(runner, activity)
        val busyHistory = mutableListOf<Boolean>()
        val observer = async(Dispatchers.Unconfined) { activity.busy.collect { busyHistory += it } }
        val first = async { serial.run(request, object : GradleRunListener {}) }
        runner.reachedGate.await()
        // The second asks while the first runs: it waits its turn, counted.
        val second = async { serial.run(request, object : GradleRunListener {}) }
        yield()
        gate.complete(Unit)
        first.await()
        second.await()
        observer.cancel()
        // Never idle between the two builds: false only at the start and at the very end.
        assertEquals(listOf(false, true, false), busyHistory)
    }
}
