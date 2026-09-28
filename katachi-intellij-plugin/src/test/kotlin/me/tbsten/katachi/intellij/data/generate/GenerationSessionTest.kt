package me.tbsten.katachi.intellij.data.generate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.GeneratedFile
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.WrittenKind
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSessionTest {
    private val fs = FakeFileSystem()
    private val arch = module(":arch-a")
    private val data = ROOT.resolve("data/src/main/kotlin/com/example/data")

    private fun item(name: String) = GenerationItem(
        templateIds = listOf(TemplateId(arch.id, "data.$name")),
        module = arch,
        args = listOf("template" to "data.$name", "onExisting" to "fail", "name" to "User"),
        expectedPaths = listOf(data.resolve("User$name.kt")),
    )

    private val items = listOf(item("Repository"), item("Service"), item("UseCase"))

    private fun output(name: String, outcome: GradleRunOutcome = GradleRunOutcome.Succeeded) =
        FakeRun(ContractFixtures.outputLines(name, ROOT), outcome)

    private fun failed(name: String) = output(name, GradleRunOutcome.Failed)

    private fun session(runner: FakeGradleTaskRunner, answer: suspend (ConflictQuestion) -> ConflictChoice = { ConflictChoice.Stop }) =
        GenerationSession(runner, fs, answer)

    private val silent = object : GenerationListener {}

    private fun roleNames(runner: FakeGradleTaskRunner) =
        runner.requests.map { request -> request.tasks.single().args.first { it.first == "template" }.second }

    @Test
    fun `一覧の順に1件ずつそのモジュールのkatachiTemplateを実行する`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> output("new") }
        val report = session(runner).run(items, OnExistingChoice.Fail, silent)

        assertEquals(listOf("data.Repository", "data.Service", "data.UseCase"), roleNames(runner))
        assertTrue(runner.requests.all { it.tasks.single().taskPath == ":arch-a:katachiTemplate" && it.linkedRootPath == ROOT })
        assertTrue(report.isComplete)
        val first = report.items.first().result as? GenerationItemResult.Generated
        assertEquals(listOf(GeneratedFile(data.resolve("UserRepository.kt"), WrittenKind.New), GeneratedFile(data.resolve("UserRepositoryImpl.kt"), WrittenKind.New)), first?.files)
    }

    @Test
    fun `onExistingは選んだ値をtemplateの次に入れる`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> output("skip") }
        session(runner).run(listOf(item("Repository")), OnExistingChoice.Skip, silent)
        assertEquals(listOf("template", "onExisting", "name"), runner.requests.single().tasks.single().args.map { it.first })
        assertEquals("skip", runner.requests.single().tasks.single().args[1].second)
    }

    @Test
    fun `上書きの出力は上書きとして記録する`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> output("overwrite") }
        val report = session(runner).run(listOf(item("Repository")), OnExistingChoice.Overwrite, silent)
        val files = (report.items.single().result as? GenerationItemResult.Generated)?.files.orEmpty()
        assertEquals(listOf(WrittenKind.Overwritten, WrittenKind.New), files.map { it.kind })
    }

    @Test
    fun `skipで何も書かれなければスキップとして次へ進む`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, index -> if (index == 0) output("skip") else output("new") }
        val report = session(runner).run(items.take(2), OnExistingChoice.Skip, silent)
        assertTrue(report.items[0].result is GenerationItemResult.Skipped)
        assertTrue(report.items[1].result is GenerationItemResult.Generated)
    }

    @Test
    fun `衝突で上書きを選ぶと同じ1件をoverwriteで再実行する`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, index -> if (index == 0) failed("conflict") else output("overwrite") }
        val questions = mutableListOf<ConflictQuestion>()
        val report = session(runner) { questions += it; ConflictChoice.Overwrite }.run(items.take(2), OnExistingChoice.Fail, silent)

        assertEquals(listOf("data.Repository", "data.Repository", "data.Service"), roleNames(runner))
        assertEquals("overwrite", runner.requests[1].tasks.single().args.first { it.first == "onExisting" }.second)
        assertEquals(ConflictQuestion(items[0].templateIds, 0, 2, listOf(data.resolve("UserRepository.kt"))), questions.single())
        assertTrue(report.isComplete)
    }

    @Test
    fun `衝突で書かずに次へを選ぶと再実行せずスキップにする`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, index -> if (index == 0) failed("conflict") else output("new") }
        val report = session(runner) { ConflictChoice.SkipAndContinue }.run(items.take(2), OnExistingChoice.Fail, silent)
        assertEquals(listOf("data.Repository", "data.Service"), roleNames(runner))
        assertEquals(GenerationItemResult.Skipped(listOf(data.resolve("UserRepository.kt"))), report.items[0].result)
    }

    @Test
    fun `衝突でここで止めるを選ぶと残りを実行しない`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, index -> if (index == 1) failed("conflict") else output("new") }
        val report = session(runner) { ConflictChoice.Stop }.run(items, OnExistingChoice.Fail, silent)
        assertEquals(2, runner.requests.size)
        assertTrue(report.items[1].result is GenerationItemResult.StoppedAtConflict)
        assertEquals(GenerationItemResult.NotRun, report.items[2].result)
        assertEquals(items[1].templateIds + items[2].templateIds, report.retryTargets)
    }

    @Test
    fun `k件目が失敗したらそこで止め書いたファイルは残して以降を未実行にする`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, index -> if (index == 1) failed("slash-in-name") else output("new") }
        val report = session(runner).run(items, OnExistingChoice.Fail, silent)
        assertEquals(2, runner.requests.size)
        assertEquals(1, report.succeededCount)
        val failure = report.items[1].result as? GenerationItemResult.Failed
        assertTrue((failure?.failure as? GenerationFailure.Katachi)?.body?.first().orEmpty().startsWith("The template data.Repository"))
        assertEquals(GenerationItemResult.NotRun, report.items[2].result)
        assertEquals(2, report.writtenFiles.size)
    }

    @Test
    fun `1件目が失敗したら何も書かずに止まる`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> failed("unknown-arg") }
        val report = session(runner).run(items, OnExistingChoice.Fail, silent)
        val failure = (report.items[0].result as? GenerationItemResult.Failed)?.failure as? GenerationFailure.NotReached
        assertTrue(failure?.failure is GradleFailure.ProcessorRejected)
        assertEquals(0, report.writtenFiles.size)
        assertEquals(items.flatMap { it.templateIds }, report.retryTargets)
    }

    @Test
    fun `生成中に定義のコンパイルが失敗したらkatachiに届かなかったとして止まる`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> failed("compile-failure") }
        val report = session(runner).run(items, OnExistingChoice.Fail, silent)
        val failure = (report.items[0].result as? GenerationItemResult.Failed)?.failure as? GenerationFailure.NotReached
        assertTrue(failure?.failure is GradleFailure.CompilationFailed)
        assertEquals(1, runner.requests.size)
    }

    @Test
    fun `実行中のキャンセルはその件を中断にし予想パスのうち実在するものを並べ以降を未実行にする`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val existing = data.resolve("UserService.kt")
        val runner = FakeGradleTaskRunner(fs) { _, index -> if (index == 1) FakeRun(gate = gate, writes = mapOf(existing to "x")) else output("new") }
        val session = session(runner)
        val running = async { session.run(items, OnExistingChoice.Fail, silent) }
        runner.reachedGate.await()
        session.cancel()
        val report = running.await()

        assertEquals(1, runner.cancelled)
        assertEquals(GenerationItemResult.Interrupted(listOf(existing)), report.items[1].result)
        assertEquals(GenerationItemResult.NotRun, report.items[2].result)
        assertEquals(2, runner.requests.size)
    }

    @Test
    fun `実行と実行の間のキャンセルは次を始めないだけにする`() = runBlocking {
        lateinit var session: GenerationSession
        val runner = FakeGradleTaskRunner(fs) { _, _ -> output("new") }
        session = session(runner)
        val listener = object : GenerationListener {
            override fun onItemFinished(index: Int, result: GenerationItemResult) {
                if (index == 0) session.cancel()
            }
        }
        val report = session.run(items, OnExistingChoice.Fail, listener)
        assertEquals(1, runner.requests.size)
        assertTrue(report.items[0].result is GenerationItemResult.Generated)
        assertEquals(listOf(GenerationItemResult.NotRun, GenerationItemResult.NotRun), report.items.drop(1).map { it.result })
    }

    @Test
    fun `書いたファイルが出力に無いままOKならファイル不明として予想パスの実在を並べる`() = runBlocking {
        fs.write(data.resolve("UserRepository.kt"), "x")
        val runner = FakeGradleTaskRunner(fs) { _, _ -> output("key-overridden") }
        val result = session(runner).run(listOf(item("Repository")), OnExistingChoice.Fail, silent).items.single().result
        assertEquals(GenerationItemResult.Generated(emptyList(), writesUnknown = true, existingExpected = listOf(data.resolve("UserRepository.kt"))), result)
    }

    @Test
    fun `まとめ行が来ないまま成功したら出力が不完全として書いた分と予想パスの実在を並べる`() = runBlocking {
        fs.write(data.resolve("UserRepository.kt"), "x")
        val runner = FakeGradleTaskRunner(fs) { _, _ -> output("truncated") }
        val result = session(runner).run(listOf(item("Repository")), OnExistingChoice.Fail, silent).items.single().result as? GenerationItemResult.Generated
        assertEquals(true, result?.outputIncomplete)
        assertEquals(listOf(data.resolve("UserRepository.kt")), result?.files?.map { it.path })
        assertEquals(listOf(data.resolve("UserRepository.kt")), result?.existingExpected)
    }

    @Test
    fun `出力の行とタスク名と各件の開始と終了を知らせる`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> output("new").copy(tasks = listOf(":arch-a:katachiTemplate")) }
        val events = mutableListOf<String>()
        session(runner).run(
            items.take(2),
            OnExistingChoice.Fail,
            object : GenerationListener {
                override fun onItemStarted(index: Int) {
                    events += "start $index"
                }

                override fun onTask(taskPath: String) {
                    events += taskPath
                }

                override fun onItemFinished(index: Int, result: GenerationItemResult) {
                    events += "finish $index"
                }
            },
        )
        assertEquals(listOf("start 0", ":arch-a:katachiTemplate", "finish 0", "start 1", ":arch-a:katachiTemplate", "finish 1"), events)
    }

    // -- One item covering several checked rows of the same module (design draft section 6, "IDE の複数選択"). --

    private fun multiItem() = GenerationItem(
        templateIds = listOf(TemplateId(arch.id, "data.Repository"), TemplateId(arch.id, "data.RepositoryImpl")),
        module = arch,
        args = listOf("template" to "data.Repository,data.RepositoryImpl", "onExisting" to "fail", "name" to "User"),
        expectedPaths = listOf(data.resolve("UserRepository.kt"), data.resolve("UserRepositoryImpl.kt")),
    )

    @Test
    fun `1つのitemに複数のtemplateIdsをまとめれば実行は1回だけで両方の行に結果が届く`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, _ -> output("new") }
        val multi = multiItem()
        val report = session(runner).run(listOf(multi), OnExistingChoice.Fail, silent)
        assertEquals(1, runner.requests.size)
        val item = report.items.single()
        assertEquals(multi.templateIds, item.templateIds)
        val result = item.result as? GenerationItemResult.Generated ?: throw AssertionError("${item.result}")
        assertEquals(2, result.files.size)
    }

    @Test
    fun `複数templateIdsのitemの衝突の質問は1回だけで両方をまとめて示す`() = runBlocking {
        val runner = FakeGradleTaskRunner(fs) { _, index -> if (index == 0) failed("conflict") else output("overwrite") }
        val multi = multiItem()
        val questions = mutableListOf<ConflictQuestion>()
        session(runner) { questions += it; ConflictChoice.Overwrite }.run(listOf(multi), OnExistingChoice.Fail, silent)
        assertEquals(1, questions.size)
        assertEquals(multi.templateIds, questions.single().templateIds)
    }

    @Test
    fun `別モジュールのitemはそれぞれ別々にkatachiTemplateを実行する`() = runBlocking {
        val archB = module(":arch-b")
        val runner = FakeGradleTaskRunner(fs) { _, _ -> output("new") }
        val itemB = item("Service").let { it.copy(module = archB, templateIds = listOf(TemplateId(archB.id, "data.Service"))) }
        session(runner).run(listOf(item("Repository"), itemB), OnExistingChoice.Fail, silent)
        assertEquals(listOf(":arch-a:katachiTemplate", ":arch-b:katachiTemplate"), runner.requests.map { it.tasks.single().taskPath })
    }
}
