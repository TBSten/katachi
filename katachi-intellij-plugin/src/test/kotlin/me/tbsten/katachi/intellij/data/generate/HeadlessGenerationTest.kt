package me.tbsten.katachi.intellij.data.generate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.presentation.FieldError
import me.tbsten.katachi.intellij.presentation.GenerateBlocker
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGenerationCatalog
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeIdeEffects
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.underRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/** A generation without a screen over fakes: what runs, in which order, and what it refuses before Gradle. */
class HeadlessGenerationTest {
    private val fs = FakeFileSystem()
    private val effects = FakeIdeEffects()
    private val rows = rowsOf("sample-jvm-with-captures")
    private val catalog = FakeGenerationCatalog { _, _ ->
        effects.log += "json"
        GenerationCatalogReload.Reloaded(listOf(DescriptionSnapshot(module(), "0.3.0", rows.map { it.template }, Instant.EPOCH)), TemplatePlacementIndex.EMPTY)
    }
    private val controller = underRoot("src/main/kotlin/com/example/controller/user/UserController.kt")
    private var build: (Int) -> FakeRun = { success(controller) }
    private val runner = FakeGradleTaskRunner(fs) { _, count -> effects.log += "gradle"; build(count) }
    private var othersRunning = false
    private var generation = HeadlessGeneration(runner, fs, catalog, effects, { othersRunning })

    private fun request(
        templates: List<String> = listOf("api.Controller"),
        arguments: Map<String, String> = mapOf("resource" to "user", "name" to "User"),
        onExisting: OnExistingChoice = OnExistingChoice.Fail,
    ) = HeadlessGenerationRequest(module(), templates, arguments, onExisting)

    private fun success(vararg files: Path) = FakeRun(
        lines = listOf("> Task :arch-a:katachiTemplate", "  [template] Generating ${files.size} files under ${ROOT.toUri()}") +
            files.map { "  [template] Wrote ${it.toUri()}" } + "BUILD SUCCESSFUL in 1s",
        writes = files.associateWith { "class X\n" },
    )

    private fun run(request: HeadlessGenerationRequest = request()) = runBlocking { generation.run(request) }

    private fun ran(result: HeadlessGenerationResult): HeadlessGenerationResult.Ran =
        result as? HeadlessGenerationResult.Ran ?: throw AssertionError("expected a run, got $result")

    @Test
    fun `読み直してから保存しラベルを付けて生成し 書いたファイルを VFS に知らせる`() {
        val report = ran(run()).report

        assertEquals(listOf("json", "save", "label", "gradle", "refresh"), effects.log)
        assertEquals(listOf("katachi (MCP): ${rows.first { it.template.template == "api.Controller" }.template.title}"), effects.labels)
        val invocation = runner.requests.single().tasks.single()
        assertEquals(listOf("template" to "api.Controller", "onExisting" to "fail", "resource" to "user", "name" to "User"), invocation.args)
        assertEquals(listOf(controller), report.writtenFiles.map { it.path })
        assertEquals(listOf(controller), effects.refreshed)
        assertTrue(catalog.ownWrites.toString(), controller in catalog.ownWrites)
    }

    @Test
    fun `skip を渡すと onExisting=skip で走らせ 既にあるファイルは skipped になる`() {
        build = { FakeRun(ContractFixtures.outputLines("skip", ROOT)) }
        val result = ran(run(request(onExisting = OnExistingChoice.Skip))).report.items.single().result

        assertEquals("skip", runner.requests.single().tasks.single().args.toMap()["onExisting"])
        assertTrue("$result", result is GenerationItemResult.Skipped)
    }

    @Test
    fun `既にあるファイルとぶつかると止めて 上書きで走らせ直さない`() {
        build = { FakeRun(ContractFixtures.outputLines("conflict", ROOT), GradleRunOutcome.Failed) }
        val result = ran(run()).report.items.single().result

        val stopped = result as? GenerationItemResult.StoppedAtConflict ?: throw AssertionError("$result")
        assertTrue(stopped.existing.isNotEmpty())
        assertEquals(1, runner.requests.size)
        assertTrue(effects.log.toString(), "conflict" !in effects.log)
    }

    @Test
    fun `足りない引数と知らない引数は Gradle を回す前に全部まとめて返す`() {
        val result = run(request(templates = listOf("api.Controller", "domain.Service"), arguments = mapOf("name" to "User", "nmae" to "typo")))

        val problems = (result as? HeadlessGenerationResult.Invalid)?.problems ?: throw AssertionError("$result")
        val fields = problems.filterIsInstance<ArgumentProblem.Field>().map { it.template to (it.blocker as? GenerateBlocker.InvalidField)?.let { b -> b.parameterName to b.error } }
        assertEquals(listOf("api.Controller" to ("resource" to FieldError.Required)), fields)
        assertEquals(listOf(ArgumentProblem.UnknownArgument("nmae")), problems.filterIsInstance<ArgumentProblem.UnknownArgument>())
        assertEquals(emptyList<Any>(), runner.requests)
        assertEquals(listOf("json"), effects.log)
    }

    @Test
    fun `知らないテンプレートは知っているものを添えて断り Gradle を回さない`() {
        val result = run(request(templates = listOf("api.Nope")))

        assertEquals(HeadlessGenerationResult.UnknownTemplates(listOf("api.Nope"), rows.map { it.template.template }), result)
        assertEquals(emptyList<Any>(), runner.requests)
    }

    @Test
    fun `IDE の生成が走っている間は読み直しもせずに busy を返す`() {
        othersRunning = true

        assertEquals(HeadlessGenerationResult.Busy, run())
        assertEquals(emptyList<Any>(), catalog.reloads)
    }

    @Test
    fun `MCP の生成が走っている間の 2 本目は busy を返す`() {
        val gate = CompletableDeferred<Unit>()
        build = { success(controller).copy(gate = gate) }
        runBlocking {
            val first = async(Dispatchers.Default) { generation.run(request()) }
            runner.reachedGate.await()
            assertTrue(generation.isRunning)
            assertEquals(HeadlessGenerationResult.Busy, generation.run(request()))
            gate.complete(Unit)
            ran(first.await())
        }
        assertEquals(1, runner.requests.size)
    }

    @Test
    fun `時間内に終わらなければ取り消して timedOut と interrupted を返す`() {
        generation = HeadlessGeneration(runner, fs, catalog, effects, { false }, timeout = 200.milliseconds)
        build = { success(controller).copy(gate = CompletableDeferred()) }

        val result = ran(run())

        assertTrue(result.timedOut)
        assertTrue("${result.report}", result.report.items.single().result is GenerationItemResult.Interrupted)
        assertEquals(1, runner.cancelled)
    }

    @Test
    fun `生成の前の読み直しが失敗すると Gradle の生成を回さずに理由を返す`() {
        val failure = LoadFailure.Gradle(GradleFailure.CompilationFailed(listOf("e: Architecture.kt:3 boom")))
        catalog.answer = { _, _ -> GenerationCatalogReload.Failed(failure) }

        assertEquals(HeadlessGenerationResult.ReloadFailed(failure), run())
        assertEquals(emptyList<Any>(), runner.requests)
    }
}
