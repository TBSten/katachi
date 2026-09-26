package me.tbsten.katachi.intellij.data.load

import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.testing.ContractFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

class LoadFailureClassifierTest {
    private fun classify(name: String) = classifyGradleFailure(ContractFixtures.outputLines(name, Paths.get("/p")))

    @Test
    fun `定義のコンパイル失敗はeの行を詳細にする`() {
        val failure = classify("compile-failure") as? GradleFailure.CompilationFailed ?: throw AssertionError()
        assertEquals(2, failure.details.size)
        assertTrue(failure.details.all { it.startsWith("e: file:///p/arch-a/") })
    }

    @Test
    fun `eの行が無くてもcompileKotlinの失敗ならコンパイル失敗にする`() {
        val failure = classifyGradleFailure(listOf("* What went wrong:", "Execution failed for task ':arch:compileTestKotlin'.", "> Compilation error"))
        assertTrue(failure is GradleFailure.CompilationFailed)
    }

    @Test
    fun `タスクが見つからなければ同期が古いとしてタスクパスを取る`() {
        val failure = classify("task-not-found") as? GradleFailure.TaskNotFound ?: throw AssertionError()
        assertEquals(":arch-a:katachiInternalTemplatesJson", failure.taskPath)
    }

    @Test
    fun `古い形のTask not foundも同期が古いにする`() {
        val failure = classifyGradleFailure(listOf("Task 'katachiInternalTemplatesJson' not found in project ':arch'."))
        assertEquals(":arch:katachiInternalTemplatesJson", (failure as? GradleFailure.TaskNotFound)?.taskPath)
    }

    @Test
    fun `プロジェクトが見つからなくても同期が古いにする`() {
        val failure = classifyGradleFailure(listOf("Project 'arch' not found in root project 'sample'."))
        assertTrue(failure is GradleFailure.TaskNotFound)
    }

    @Test
    fun `architecture未設定はその段落を詳細にする`() {
        val failure = classify("architecture-not-set") as? GradleFailure.ArchitectureNotSet ?: throw AssertionError()
        assertTrue(failure.details.single().contains("katachi { architecture = ... } is not set"))
    }

    @Test
    fun `processorが走る前にkatachiが断った失敗は例外の本文をスタックトレース抜きで持つ`() {
        val failure = classify("unknown-arg") as? GradleFailure.ProcessorRejected ?: throw AssertionError()
        assertEquals("me.tbsten.katachi.processor.KatachiUnknownProcessorArgException", failure.exceptionClassName)
        assertEquals("Unknown processor argument(s): implSuffix.", failure.details.first())
        assertTrue(failure.details.last().startsWith("Known arguments:"))
        assertTrue(failure.details.none { it.trimStart().startsWith("at ") })
    }

    @Test
    fun `どれでもなければ最後の数行を詳細にする`() {
        val failure = classify("other-failure") as? GradleFailure.Other ?: throw AssertionError()
        assertEquals("BUILD FAILED in 1s", failure.details.last())
        assertTrue(failure.details.any { it.contains("Could not find me.tbsten.katachi:katachi:9.9.9") })
    }

    @Test
    fun `ANSIの色コードを落としてから判定する`() {
        val failure = classifyGradleFailure(listOf("\u001B[31me: file:///a.kt:1:1 Unresolved reference 'x'.\u001B[0m"))
        assertEquals(listOf("e: file:///a.kt:1:1 Unresolved reference 'x'."), (failure as? GradleFailure.CompilationFailed)?.details)
    }
}
