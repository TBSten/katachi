package me.tbsten.katachi.intellij.data.load

import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.KatachiCapability
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * What [DefinitionLoader] does when the run fails and katachi left `definition-errors.json` (ide-authoring design 2.3):
 * a file this run wrote and that holds a mistake makes the failure `DefinitionRejected`; anything else is classified
 * from the output as it was.
 */
class DefinitionLoaderRejectedTest {
    private val fs = FakeFileSystem()
    private val arch = module(":arch").copy(capabilities = setOf(KatachiCapability.Definition, KatachiCapability.Templates))
    private val old = module(":old")
    private val noListener = object : GradleRunListener {}
    private val rejectedJson = ContractFixtures.text("authoring/definition-errors.json")
    private val emptyJson = ContractFixtures.text("authoring/definition-errors-empty.json")
    private val rejectedLog = ContractFixtures.outputLines("definition-rejected", arch.directory)

    /** The fake file system stamps the files a run writes 2026-09-27; a file put there earlier is from "the run before". */
    private val theRunBefore = Instant.parse("2026-09-26T00:00:00Z")

    private fun failedRun(errors: String?, lines: List<String> = rejectedLog) = FakeRun(
        lines = lines,
        outcome = GradleRunOutcome.Failed,
        writes = errors?.let { mapOf(arch.definitionErrorsJson to it) }.orEmpty(),
    )

    private fun load(run: FakeRun, module: me.tbsten.katachi.intellij.model.KatachiModule = arch): LoadResult {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> run }
        return runBlocking { DefinitionLoader(runner, fs).load(listOf(module), emptyMap(), noListener) }
    }

    private fun failureOf(result: LoadResult): GradleFailure = ((result as? LoadResult.Failed)?.failure as? LoadFailure.Gradle)?.failure
        ?: throw AssertionError("$result")

    @Test
    fun `この実行が書いた誤りの JSON があれば定義の誤りとして失敗し 行と本文を持つ`() {
        val failure = failureOf(load(failedRun(rejectedJson))) as? GradleFailure.DefinitionRejected ?: throw AssertionError()

        assertEquals(2, failure.errors.size)
        assertEquals(DeclarationSiteRef("ScreenRole.kt", 4), failure.errors.first().site)
        assertEquals(arch.id, failure.errors.first().module)
        assertEquals(failure.errors.first().message.lines(), failure.details)
    }

    @Test
    fun `実行の前からあった誤りの JSON を書き換えずに落ちた実行は 前の実行の残りとして使わず 出力から分類する`() {
        fs.write(arch.definitionErrorsJson, rejectedJson)
        // The run fails with nothing written, and the file is exactly as the run before left it, however soon this run came.
        val failure = failureOf(load(failedRun(null)))
        assertTrue("$failure", failure is GradleFailure.ProcessorRejected)
    }

    @Test
    fun `実行の前からあった誤りの JSON を書き換えた実行は 今回のものとして読む`() {
        fs.write(arch.definitionErrorsJson, rejectedJson, at = theRunBefore)
        assertTrue(failureOf(load(failedRun(rejectedJson))) is GradleFailure.DefinitionRejected)
    }

    @Test
    fun `誤りが空の JSON や無い JSON や壊れた JSON は出力からの分類のまま`() {
        for (errors in listOf(emptyJson, null, "{", rejectedJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": 9"))) {
            val failure = failureOf(load(failedRun(errors)))
            assertTrue("$errors -> $failure", failure is GradleFailure.ProcessorRejected)
        }
        assertTrue(failureOf(load(failedRun(emptyJson, lines = listOf("e: a.kt: broken")))) is GradleFailure.CompilationFailed)
    }

    @Test
    fun `誤りの JSON を書かない katachi 0_2 のモジュールは今までどおり分類する`() {
        val failure = failureOf(load(failedRun(rejectedJson), module = old))
        assertTrue(failure is GradleFailure.ProcessorRejected)
    }

    @Test
    fun `成功した実行は誤りの JSON が残っていても読まない`() {
        val definition = DefinitionFixtures.definitionText("synthetic-definition-basic")
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun(writes = mapOf(arch.definitionJson to definition, arch.definitionErrorsJson to rejectedJson)) }
        val result = runBlocking { DefinitionLoader(runner, fs).load(listOf(arch), emptyMap(), noListener) }
        assertTrue("$result", result is LoadResult.Loaded)
    }

    @Test
    fun `タスクを知らない候補は誤りの JSON を見る前に TaskNotFound として次のタスクへ進む`() = runBlocking {
        val candidate = module(":cand").copy(capabilities = emptySet())
        val runner = FakeGradleTaskRunner(fs) { _, index ->
            if (index == 0) FakeRun(lines = listOf("Task 'katachiInternalDefinitionJson' not found in project ':cand'."), outcome = GradleRunOutcome.Failed)
            else FakeRun(lines = rejectedLog, outcome = GradleRunOutcome.Failed)
        }
        val result = DefinitionLoader(runner, fs).loadCandidates(listOf(candidate), emptyMap(), noListener)
        assertTrue("$result", failureOf(result) is GradleFailure.ProcessorRejected)
    }

    @Test
    fun `候補の読み込みで定義の誤りの JSON があればそれで失敗する`() = runBlocking {
        val candidate = module(":cand").copy(capabilities = emptySet())
        val json = rejectedJson
        val runner = FakeGradleTaskRunner(fs) { _, _ ->
            FakeRun(lines = rejectedLog, outcome = GradleRunOutcome.Failed, writes = mapOf(candidate.definitionErrorsJson to json))
        }
        val result = DefinitionLoader(runner, fs).loadCandidates(listOf(candidate), emptyMap(), noListener)
        assertTrue("$result", failureOf(result) is GradleFailure.DefinitionRejected)
    }
}
