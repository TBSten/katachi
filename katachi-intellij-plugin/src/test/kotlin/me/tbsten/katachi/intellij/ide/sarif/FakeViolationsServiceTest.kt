package me.tbsten.katachi.intellij.ide.sarif

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.testing.ViolationsStateScripts
import me.tbsten.katachi.intellij.testing.module
import java.nio.file.Paths

/** The fake keeps the real service's contract: answers follow the state, and a check runs until the test finishes it. */
internal class FakeViolationsServiceTest : BasePlatformTestCase() {
    private val module = module(":arch").id
    private val sarif = Paths.get("/w/arch/build/reports/katachi/katachi.sarif")

    fun `test 状態から違反をファイルごとに引き NotRun では何も返さない`() {
        val file = LightVirtualFile("A.kt")
        val other = LightVirtualFile("B.kt")
        val violation = ViolationsStateScripts.violation(module, Paths.get(file.path))
        val service = FakeViolationsService()

        assertEquals(emptyList<Any>(), service.forFile(file))
        service.emit(ViolationsState.Ready(listOf(violation), java.time.Instant.EPOCH, stale = false, paths = listOf(sarif)))

        assertEquals(listOf(violation), service.forFile(file))
        assertEquals(emptyList<Any>(), service.forFile(other))
        assertEquals(listOf(violation), service.forModule(module))
    }

    fun `test 標準の列を1つずつ流しても答えは状態に従い Unreadable では前の答えが残る`() {
        val file = LightVirtualFile("A.kt")
        val violations = listOf(
            ViolationsStateScripts.violation(module, Paths.get(file.path)),
            ViolationsStateScripts.violation(module, Paths.get(file.path), "katachi.MissingFile"),
        )
        val service = FakeViolationsService()
        val states = ViolationsStateScripts.standardCycle(violations, sarif)

        val answers = mutableListOf<Int>()
        service.play(states) { state ->
            answers += service.forFile(file).size
            assertEquals(state.answer?.runs?.size ?: 0, answers.last())
        }

        assertEquals(states, service.history)
        assertEquals(listOf(0, 2, 2, 2, 1, 0), answers)
    }

    fun `test scripted な答えは状態に答えがあるときだけ返る`() {
        val file = LightVirtualFile("A.kt")
        val violation = ViolationsStateScripts.violation(module, Paths.get("/elsewhere/X.kt"))
        val service = FakeViolationsService()
        service.answer(Paths.get(file.path), listOf(violation))

        assertEquals(emptyList<Any>(), service.forFile(file))
        service.emit(ViolationsState.Ready(emptyList(), java.time.Instant.EPOCH, stale = false, paths = listOf(sarif)))
        assertEquals(listOf(violation), service.forFile(file))
    }

    fun `test runCheck は走っている間 runningChecks に入り同じ Deferred を返し finishCheck で完了する`() {
        val service = FakeViolationsService()
        val first = service.runCheck(module)
        val again = service.runCheck(module)

        assertSame(first, again)
        assertEquals(setOf(module), service.runningChecks.value)
        assertEquals(listOf(module, module), service.runCheckCalls)
        assertFalse(first.isCompleted)

        service.finishCheck(module, GradleRunOutcome.Failed)

        assertTrue(first.isCompleted)
        assertEquals(emptySet<Any>(), service.runningChecks.value)
        // A check after the first ended is a new run.
        val next = service.runCheck(module)
        assertNotSame(first, next)
        service.finishCheck(module)
        assertEquals(GradleRunOutcome.Succeeded, runBlocking { next.await() })
    }

    fun `test refresh の回数と step の列を記録する`() {
        val service = FakeViolationsService()
        service.refresh()
        service.refresh()
        assertEquals(2, service.refreshCalls)

        val states = ViolationsStateScripts.standardCycle(emptyList(), sarif)
        service.script(*states.drop(1).toTypedArray())
        val observed = mutableListOf(service.state.value)
        while (service.step()) observed += service.state.value
        assertEquals(states, observed)
    }
}
