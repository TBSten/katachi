package me.tbsten.katachi.intellij.data.load

import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.model.KatachiCapability
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

/** The definition side of [DefinitionLoader] (design 4.2); the templates side is `TemplateDescriptionLoaderTest`. */
class DefinitionLoaderTest {
    private val fs = FakeFileSystem()
    private val arch = module(":arch").copy(capabilities = setOf(KatachiCapability.Definition, KatachiCapability.Templates))
    private val old = module(":old")
    private val noListener = object : GradleRunListener {}
    private val definition = DefinitionFixtures.definitionText("synthetic-definition-basic")

    @Test
    fun `定義タスクを持つモジュールは定義タスクだけを走らせ定義とテンプレートを1つの JSON から読む`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun(writes = mapOf(arch.definitionJson to definition)) }
        val result = DefinitionLoader(runner, fs).load(listOf(arch), emptyMap(), noListener)

        assertEquals(listOf(":arch:katachiInternalDefinitionJson"), runner.requests.single().taskNames)
        val snapshot = (result as? LoadResult.Loaded)?.snapshots?.single() ?: throw AssertionError("$result")
        assertEquals(DefinitionFixtures.parsed("synthetic-definition-basic").definition.roles.map { it.qualifiedName }, snapshot.definition?.roles?.map { it.qualifiedName })
        assertEquals(DefinitionFixtures.parsed("synthetic-definition-basic").templates.map { it.template }, snapshot.templates.map { it.template })
    }

    @Test
    fun `katachi 0_2 のモジュールと混ざれば1回の実行にそれぞれのタスクを並べる`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ ->
            FakeRun(writes = mapOf(arch.definitionJson to definition, old.templateDescriptionJson to ContractFixtures.json("arch-a")))
        }
        val result = DefinitionLoader(runner, fs).load(listOf(arch, old), emptyMap(), noListener) as LoadResult.Loaded

        assertEquals(listOf(":arch:katachiInternalDefinitionJson", ":old:katachiInternalTemplatesJson"), runner.requests.single().taskNames)
        assertNotNull(result.snapshots[0].definition)
        assertNull(result.snapshots[1].definition)
    }

    @Test
    fun `読めない定義 JSON は既存の失敗に写す`() = runBlocking {
        fun failureOf(text: String): LoadFailure {
            val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun(writes = mapOf(arch.definitionJson to text)) }
            return (runBlocking { DefinitionLoader(runner, fs).load(listOf(arch), emptyMap(), noListener) } as LoadResult.Failed).failure
        }
        assertTrue(failureOf("{") is LoadFailure.MalformedJson)
        assertEquals("schemaVersion", (failureOf(definition.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")) as LoadFailure.IncompatibleJson).location)
        assertEquals("katachiVersion", (failureOf(definition.replace("\"katachiVersion\"", "\"renamed\"")) as LoadFailure.IncompatibleJson).location)
    }

    @Test
    fun `定義タスクが何も書かなければ JsonMissing`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun() }
        val result = DefinitionLoader(runner, fs).load(listOf(arch), emptyMap(), noListener) as LoadResult.Failed
        assertEquals(LoadFailure.JsonMissing(arch.definitionJson), result.failure)
    }

    @Test
    fun `キャッシュは定義モジュールなら definition_json を読む`() {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> throw AssertionError("must not run Gradle") }
        fs.write(arch.definitionJson, definition)
        fs.write(arch.templateDescriptionJson, ContractFixtures.json("arch-a"))
        val cached = DefinitionLoader(runner, fs).readCached(listOf(arch), emptyMap())
        assertNotNull(cached.single().definition)
    }

    @Test
    fun `タスク一覧の無い候補は定義タスクを先に試し無ければテンプレートのタスクに落とす`() = runBlocking {
        val candidate = module(":arch").copy(capabilities = emptySet())
        val notFound = ContractFixtures.outputLines("task-not-found", Paths.get("/p"))
        val runner = FakeGradleTaskRunner(fs) { request, _ ->
            if (request.taskNames.single().endsWith("katachiInternalDefinitionJson")) {
                FakeRun(notFound, GradleRunOutcome.Failed)
            } else {
                FakeRun(writes = mapOf(candidate.templateDescriptionJson to ContractFixtures.json("arch-a")))
            }
        }
        val result = DefinitionLoader(runner, fs).loadCandidates(listOf(candidate), emptyMap(), noListener) as LoadResult.Loaded

        assertEquals(listOf(listOf(":arch:katachiInternalDefinitionJson"), listOf(":arch:katachiInternalTemplatesJson")), runner.requests.map { it.taskNames })
        assertEquals(setOf(KatachiCapability.Templates), result.snapshots.single().module.capabilities)
    }

    @Test
    fun `タスク一覧の無い候補が定義タスクに答えれば定義を読む`() = runBlocking {
        val candidate = module(":arch").copy(capabilities = emptySet())
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun(writes = mapOf(candidate.definitionJson to definition)) }
        val result = DefinitionLoader(runner, fs).loadCandidates(listOf(candidate), emptyMap(), noListener) as LoadResult.Loaded
        assertEquals(1, runner.requests.size)
        assertEquals(setOf(KatachiCapability.Definition), result.snapshots.single().module.capabilities)
        assertNotNull(result.snapshots.single().definition)
    }
}
