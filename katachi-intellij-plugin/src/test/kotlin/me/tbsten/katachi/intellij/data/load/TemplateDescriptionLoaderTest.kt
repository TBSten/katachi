package me.tbsten.katachi.intellij.data.load

import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths
import java.time.Instant

class TemplateDescriptionLoaderTest {
    private val fs = FakeFileSystem()
    private val archA = module(":arch-a")
    private val archB = module(":arch-b")
    private val noListener = object : GradleRunListener {}

    private fun writesBoth() = mapOf(
        archA.templateDescriptionJson to ContractFixtures.json("arch-a"),
        archB.templateDescriptionJson to ContractFixtures.json("arch-b"),
    )

    @Test
    fun `複数モジュールのタスクを1回のGradle実行に並べて各JSONを読む`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun(writes = writesBoth()) }
        val result = TemplateDescriptionLoader(runner, fs).load(listOf(archA, archB), emptyMap(), noListener)

        assertEquals(1, runner.requests.size)
        assertEquals(listOf(":arch-a:katachiInternalTemplatesJson", ":arch-b:katachiInternalTemplatesJson"), runner.requests.single().taskNames)
        val loaded = result as? LoadResult.Loaded ?: throw AssertionError("$result")
        assertEquals(listOf(":arch-a", ":arch-b"), loaded.snapshots.map { it.module.gradlePath })
        assertEquals("data.Repository", loaded.snapshots[1].templates.single().roleName)
    }

    @Test
    fun `別々のGradleルートはルートごとに1回ずつ実行する`() = runBlocking {
        val other = module(":arch", root = Paths.get("/work/other"), rootName = "other")
        fs.write(other.templateDescriptionJson, ContractFixtures.json("arch-b"))
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun(writes = writesBoth()) }
        val result = TemplateDescriptionLoader(runner, fs).load(listOf(archA, other), emptyMap(), noListener)

        assertEquals(listOf(archA.linkedRootPath, other.linkedRootPath), runner.requests.map { it.linkedRootPath })
        assertTrue(result is LoadResult.Loaded)
    }

    @Test
    fun `JSONの更新時刻を前回の読み込み時刻にし版を添える`() = runBlocking {
        val at = Instant.parse("2026-09-27T01:02:03Z")
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun() }
        fs.write(archA.templateDescriptionJson, ContractFixtures.json("arch-a"), at)
        val result = TemplateDescriptionLoader(runner, fs).load(listOf(archA), mapOf(archA.id to "0.3.0"), noListener)
        val snapshot = (result as? LoadResult.Loaded)?.snapshots?.single() ?: throw AssertionError()
        assertEquals(at, snapshot.loadedAt)
        assertEquals("0.3.0", snapshot.katachiVersion)
    }

    @Test
    fun `Gradleが失敗したら出力から原因を分類する`() = runBlocking {
        val lines = ContractFixtures.outputLines("task-not-found", Paths.get("/p"))
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun(lines, GradleRunOutcome.Failed) }
        val result = TemplateDescriptionLoader(runner, fs).load(listOf(archA), emptyMap(), noListener) as? LoadResult.Failed
        val failure = (result?.failure as? LoadFailure.Gradle)?.failure
        assertTrue("$failure", failure is GradleFailure.TaskNotFound)
        assertEquals(lines, result?.output)
    }

    @Test
    fun `成功したのにJSONが無ければその場所を名指しする`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun() }
        val result = TemplateDescriptionLoader(runner, fs).load(listOf(archA), emptyMap(), noListener) as? LoadResult.Failed
        assertEquals(LoadFailure.JsonMissing(archA.templateDescriptionJson), result?.failure)
    }

    @Test
    fun `壊れたJSONと版の合わないJSONを別の失敗にする`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun() }
        val loader = TemplateDescriptionLoader(runner, fs)

        fs.write(archA.templateDescriptionJson, """{"templates": [""")
        assertTrue((loader.load(listOf(archA), emptyMap(), noListener) as? LoadResult.Failed)?.failure is LoadFailure.MalformedJson)

        fs.write(archA.templateDescriptionJson, """{"templates": []}""")
        val incompatible = (loader.load(listOf(archA), emptyMap(), noListener) as? LoadResult.Failed)?.failure as? LoadFailure.IncompatibleJson
        assertEquals("$.details", incompatible?.location)
    }

    @Test
    fun `進み具合のタスク名と出力の行を流す`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun(lines = listOf("a", "b"), tasks = listOf(":arch-a:compileTestKotlin"), writes = writesBoth()) }
        val tasks = mutableListOf<String>()
        val lines = mutableListOf<String>()
        TemplateDescriptionLoader(runner, fs).load(
            listOf(archA),
            emptyMap(),
            object : GradleRunListener {
                override fun onLine(line: String) {
                    lines += line
                }

                override fun onTask(taskPath: String) {
                    tasks += taskPath
                }
            },
        )
        assertEquals(listOf(":arch-a:compileTestKotlin"), tasks)
        assertEquals(listOf("a", "b"), lines)
    }

    @Test
    fun `キャッシュはディスクのJSONだけを読み無いものと壊れたものは飛ばす`() {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> throw AssertionError("must not run Gradle") }
        fs.write(archA.templateDescriptionJson, ContractFixtures.json("arch-a"))
        fs.write(archB.templateDescriptionJson, "{")
        val cached = TemplateDescriptionLoader(runner, fs).readCached(listOf(archA, archB, module(":arch-c")), emptyMap())
        assertEquals(listOf(":arch-a"), cached.map { it.module.gradlePath })
    }

    @Test
    fun `cleanでJSONが消えていればキャッシュは空で初回扱いになる`() {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> throw AssertionError("must not run Gradle") }
        assertEquals(emptyList<Any>(), TemplateDescriptionLoader(runner, fs).readCached(listOf(archA), emptyMap()))
    }

    @Test
    fun `タスク一覧の無い候補はモジュールごとに実行しタスクが無いものを外す`() = runBlocking {
        val lines = ContractFixtures.outputLines("task-not-found", Paths.get("/p"))
        val runner = FakeGradleTaskRunner(fs) { request, _ ->
            if (request.taskNames.single().startsWith(":arch-a")) FakeRun(writes = writesBoth()) else FakeRun(lines, GradleRunOutcome.Failed)
        }
        val result = TemplateDescriptionLoader(runner, fs).loadCandidates(listOf(archA, module(":app")), emptyMap(), noListener)
        assertEquals(2, runner.requests.size)
        assertEquals(listOf(":arch-a"), (result as? LoadResult.Loaded)?.snapshots?.map { it.module.gradlePath })
    }

    @Test
    fun `候補の実行がタスク無し以外で失敗したらそこで失敗にする`() = runBlocking {
        val lines = ContractFixtures.outputLines("compile-failure", Paths.get("/p"))
        val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun(lines, GradleRunOutcome.Failed) }
        val result = TemplateDescriptionLoader(runner, fs).loadCandidates(listOf(archA), emptyMap(), noListener)
        assertTrue(((result as? LoadResult.Failed)?.failure as? LoadFailure.Gradle)?.failure is GradleFailure.CompilationFailed)
    }
}
