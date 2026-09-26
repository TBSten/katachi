package me.tbsten.katachi.intellij.data.gradle

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ROOT
import org.junit.Assert.assertEquals
import org.junit.Test

class SerialGradleTaskRunnerTest {
    private fun request(task: String) = GradleRunRequest(ROOT, listOf(GradleTaskInvocation(task, emptyList())))

    @Test
    fun `実行中のビルドがあれば次のビルドはその終わりを待ってから始まる`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val fake = FakeGradleTaskRunner { request, _ -> if (request.taskNames.single() == ":first") FakeRun(gate = gate) else FakeRun() }
        val runner = SerialGradleTaskRunner(fake)

        val first = async { runner.run(request(":first"), object : GradleRunListener {}) }
        fake.reachedGate.await()
        val second = async { runner.run(request(":second"), object : GradleRunListener {}) }
        repeat(10) { yield() }
        assertEquals(listOf(listOf(":first")), fake.requests.map { it.taskNames })

        gate.complete(Unit)
        first.await()
        second.await()
        assertEquals(listOf(listOf(":first"), listOf(":second")), fake.requests.map { it.taskNames })
    }
}
