package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeIdeEffects
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KatachiToolWindowViewModelTest {
    private val fs = FakeFileSystem()
    private val effects = FakeIdeEffects()
    private val arch = module(":arch-a")
    private val json = arch.templateDescriptionJson
    private val repository = TemplateId(arch.id, "data/Repository")
    private val useCase = TemplateId(arch.id, "domain/UseCase")
    private val data = ROOT.resolve("data/src/main/kotlin/com/example/data")

    private var synced: SyncedProject = SyncedProject.Synced(
        listOf(SyncedRoot(ROOT, "project", listOf(SyncedModule(":arch-a", arch.directory, setOf("katachiInternalTemplatesJson"), "0.3.0")))),
    )

    /** Loads write [loadJson] (after [loadGate], when set); generations answer with [generate]. */
    private var loadJson: String? = ContractFixtures.json("arch-a")
    private var loadGate: CompletableDeferred<Unit>? = null
    private var loadOutcome: FakeRun? = null
    private var loadThrows: Exception? = null
    private var generate: (Int) -> FakeRun = { FakeRun(ContractFixtures.outputLines("new", ROOT)) }
    private var generations = 0

    private val runner = FakeGradleTaskRunner(fs) { request, _ -> answer(request) }

    private fun answer(request: GradleRunRequest): FakeRun =
        if (request.taskNames.first().endsWith("katachiInternalTemplatesJson")) {
            loadThrows?.let { throw it }
            loadOutcome ?: FakeRun(writes = listOfNotNull(loadJson?.let { json to it }).toMap(), gate = loadGate)
        } else {
            generate(generations++)
        }

    private fun CoroutineScope.viewModel() = KatachiToolWindowViewModel(this, { synced }, runner, fs, effects)

    private suspend fun KatachiToolWindowViewModel.await(predicate: (KatachiScreenState) -> Boolean): KatachiScreenState =
        withTimeout(5_000) { state.first(predicate) }

    private suspend fun KatachiToolWindowViewModel.loaded(): KatachiScreenState {
        dispatch(KatachiIntent.Opened)
        return await { it.phase == ScreenPhase.Ready && it.loading == null }
    }

    private fun KatachiToolWindowViewModel.fillRepository(name: String = "User") {
        dispatch(KatachiIntent.ToggleCheck(repository))
        dispatch(KatachiIntent.Input(FieldId(repository, "name"), name))
    }

    private suspend fun KatachiToolWindowViewModel.finished(): GenerationState.Finished =
        await { it.generation is GenerationState.Finished }.generation as? GenerationState.Finished ?: throw AssertionError()

    @Test
    fun `開くと保存してから定義モジュールのJSONを読み込み一覧にする`() = runBlocking {
        val vm = viewModel()
        val state = vm.loaded()
        assertEquals(7, state.rows.size)
        assertEquals(listOf(":arch-a:katachiInternalTemplatesJson"), runner.requests.single().taskNames)
        assertEquals(listOf("save"), effects.log)
    }

    @Test
    fun `二度目に開いても読み込み直さない`() = runBlocking {
        val vm = viewModel()
        vm.loaded()
        vm.dispatch(KatachiIntent.Opened)
        assertEquals(1, runner.requests.size)
    }

    @Test
    fun `キャッシュを先に出して裏で更新し更新中も一覧を操作できる`() = runBlocking {
        fs.write(json, ContractFixtures.json("arch-b"))
        loadGate = CompletableDeferred()
        val vm = viewModel()
        vm.dispatch(KatachiIntent.Opened)
        val refreshing = vm.await { it.loading != null }
        assertEquals(listOf("data/Repository"), refreshing.rows.map { it.template.roleName })
        assertEquals(false, refreshing.loading?.isInitial)
        assertEquals(ScreenPhase.Ready, refreshing.phase)

        runner.reachedGate.await()
        vm.dispatch(KatachiIntent.ToggleCheck(repository))
        assertEquals(listOf(repository), vm.state.value.form.selected)

        loadGate?.complete(Unit)
        val updated = vm.await { it.loading == null }
        assertEquals(7, updated.rows.size)
    }

    @Test
    fun `キャッシュが無ければ初回の読み込み中として全面に進み具合を出す`() = runBlocking {
        loadGate = CompletableDeferred()
        val vm = viewModel()
        vm.dispatch(KatachiIntent.Opened)
        val loading = vm.await { it.loading != null }
        assertEquals(true, loading.loading?.isInitial)
        assertEquals(BusyState.InitialLoading, loading.busy)
        loadGate?.complete(Unit)
        vm.await { it.loading == null }
        Unit
    }

    @Test
    fun `再読み込みで消えたテンプレートのチェックを外し残った入力は残す`() = runBlocking {
        val vm = viewModel()
        vm.loaded()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.ToggleCheck(useCase))

        loadJson = ContractFixtures.json("arch-a").replace("\"domain/UseCase\"", "\"domain/Renamed\"")
        vm.dispatch(KatachiIntent.Reload)
        val reloaded = vm.await { it.removedTemplates.isNotEmpty() }
        assertEquals(listOf(useCase), reloaded.removedTemplates)
        assertEquals(listOf(repository), reloaded.form.selected)
        assertEquals("User", reloaded.form.inputOf(FieldId(repository, "name")))
        assertEquals(listOf("save", "save"), effects.log)
    }

    @Test
    fun `チェックと入力から生成し保存とラベルのあとに書いたファイルを反映して最初の1つを開く`() = runBlocking {
        val vm = viewModel()
        vm.loaded()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.Generate)
        val finished = vm.finished()

        assertEquals("katachi: before generating (Repository)", finished.localHistoryLabel)
        assertEquals(listOf("save", "save", "label", "refresh", "open", "notifyGenerated"), effects.log)
        assertEquals(listOf(data.resolve("UserRepository.kt")), effects.opened)
        assertEquals(2, effects.refreshed.size)
        val args = runner.requests.last().tasks.single().args
        assertEquals(listOf("roleName" to "data/Repository", "onExisting" to "fail", "name" to "User"), args)
    }

    @Test
    fun `生成できない状態で生成を押しても何もしない`() = runBlocking {
        val vm = viewModel()
        vm.loaded()
        vm.dispatch(KatachiIntent.ToggleCheck(repository))
        vm.dispatch(KatachiIntent.Generate)
        assertNull(vm.state.value.generation)
        assertEquals(1, runner.requests.size)
    }

    @Test
    fun `衝突したら確認待ちを出して答えを待ち上書きで再実行する`() = runBlocking {
        generate = { index -> if (index == 0) FakeRun(ContractFixtures.outputLines("conflict", ROOT), GradleRunOutcome.Failed) else FakeRun(ContractFixtures.outputLines("overwrite", ROOT)) }
        val vm = viewModel()
        var seenWhileAsking: KatachiScreenState? = null
        effects.conflictAnswer = {
            seenWhileAsking = vm.state.value
            ConflictChoice.Overwrite
        }
        vm.loaded()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.Generate)
        val finished = vm.finished()

        val running = seenWhileAsking?.generation as? GenerationState.Running ?: throw AssertionError()
        assertEquals(GenerationRowStatus.AwaitingConflict, running.statuses[repository])
        assertEquals(repository, running.conflict?.templateId)
        assertTrue(finished.report.isComplete)
        assertEquals(3, runner.requests.size)
    }

    @Test
    fun `途中で失敗したら結果を出し残りをやり直すで失敗と未実行の行だけ残す`() = runBlocking {
        generate = { index -> if (index == 0) FakeRun(ContractFixtures.outputLines("new", ROOT)) else FakeRun(ContractFixtures.outputLines("unknown-arg", ROOT), GradleRunOutcome.Failed) }
        val vm = viewModel()
        vm.loaded()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.ToggleCheck(useCase))
        vm.dispatch(KatachiIntent.Generate)
        val finished = vm.finished()
        val failure = finished.report.items[1].result as? GenerationItemResult.Failed
        assertTrue(failure != null)

        vm.dispatch(KatachiIntent.RetryRemaining)
        val back = vm.state.value
        assertNull(back.generation)
        assertEquals(listOf(useCase), back.form.selected)
        assertEquals("User", back.form.inputOf(FieldId(useCase, "name")))
    }

    @Test
    fun `生成中のキャンセルで実行中の件を中断にする`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        generate = { FakeRun(gate = gate) }
        val vm = viewModel()
        vm.loaded()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.Generate)
        runner.reachedGate.await()
        assertEquals(BusyState.Generating, vm.state.value.busy)

        vm.dispatch(KatachiIntent.CancelGeneration)
        val finished = vm.finished()
        assertTrue(finished.report.items.single().result is GenerationItemResult.Interrupted)
        assertEquals(1, runner.cancelled)
    }

    @Test
    fun `生成中は再読み込みと入力を受け付けない`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        generate = { FakeRun(ContractFixtures.outputLines("new", ROOT), gate = gate) }
        val vm = viewModel()
        vm.loaded()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.Generate)
        runner.reachedGate.await()
        vm.dispatch(KatachiIntent.Reload)
        vm.dispatch(KatachiIntent.Input(FieldId(repository, "name"), "Other"))
        assertEquals(2, runner.requests.size)
        assertEquals("User", vm.state.value.form.inputOf(FieldId(repository, "name")))
        gate.complete(Unit)
        vm.finished()
        Unit
    }

    @Test
    fun `キャッシュありの読み込み中に押した生成は読み込みの完了を待ってから始める`() = runBlocking {
        fs.write(json, ContractFixtures.json("arch-a"))
        loadGate = CompletableDeferred()
        val vm = viewModel()
        vm.dispatch(KatachiIntent.Opened)
        runner.reachedGate.await()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.Generate)
        val waiting = vm.state.value.generation as? GenerationState.Running
        assertEquals(true, waiting?.waitingForLoad)
        assertEquals(1, runner.requests.size)

        loadGate?.complete(Unit)
        vm.finished()
        assertEquals(2, runner.requests.size)
        assertEquals(listOf("save", "save", "label", "refresh", "open", "notifyGenerated"), effects.log)
    }

    @Test
    fun `結果から続けて生成に戻るとStringの欄だけ空になる`() = runBlocking {
        val vm = viewModel()
        vm.loaded()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.Input(FieldId(repository, "withImpl"), "false"))
        vm.dispatch(KatachiIntent.Generate)
        vm.finished()
        vm.dispatch(KatachiIntent.ContinueGenerating)
        val form = vm.state.value.form
        assertEquals(listOf(repository), form.selected)
        assertEquals(mapOf("withImpl" to "false"), form.inputsOf(repository))
    }

    @Test
    fun `キャッシュが無いまま読み込みに失敗したらエラー画面にする`() = runBlocking {
        loadOutcome = FakeRun(ContractFixtures.outputLines("compile-failure", ROOT), GradleRunOutcome.Failed)
        val vm = viewModel()
        vm.dispatch(KatachiIntent.Opened)
        val error = vm.await { it.phase is ScreenPhase.LoadError }.phase as? ScreenPhase.LoadError
        assertTrue(((error?.failure as? LoadFailure.Gradle)?.failure) is GradleFailure.CompilationFailed)
    }

    @Test
    fun `キャッシュがあれば読み込みに失敗しても一覧を出したまま帯にする`() = runBlocking {
        fs.write(json, ContractFixtures.json("arch-a"))
        loadOutcome = FakeRun(ContractFixtures.outputLines("task-not-found", ROOT), GradleRunOutcome.Failed)
        val vm = viewModel()
        vm.dispatch(KatachiIntent.Opened)
        val state = vm.await { it.loadErrorBanner != null }
        assertEquals(ScreenPhase.Ready, state.phase)
        assertTrue(((state.loadErrorBanner as? LoadFailure.Gradle)?.failure) is GradleFailure.TaskNotFound)
    }

    @Test
    fun `キャッシュ無しの読み込みを止めたら止めたことをエラーとして出す`() = runBlocking {
        loadGate = CompletableDeferred()
        val vm = viewModel()
        vm.dispatch(KatachiIntent.Opened)
        runner.reachedGate.await()
        vm.dispatch(KatachiIntent.CancelLoad)
        val stopped = vm.await { it.phase is ScreenPhase.LoadError }
        assertEquals(LoadFailure.Cancelled, (stopped.phase as? ScreenPhase.LoadError)?.failure)
    }

    @Test
    fun `テンプレートが0件なら空状態にする`() = runBlocking {
        loadJson = """{"templates": [], "details": []}"""
        val vm = viewModel()
        vm.dispatch(KatachiIntent.Opened)
        assertEquals(ScreenPhase.Empty(EmptyReason.NoTemplates), vm.await { it.phase is ScreenPhase.Empty }.phase)
    }

    @Test
    fun `Gradleでない同期前未導入古い版はGradleを走らせずに空状態にする`() = runBlocking {
        val cases = listOf(
            SyncedProject.NotGradle to EmptyReason.NotGradle,
            SyncedProject.NotSynced to EmptyReason.NotSynced,
            SyncedProject.Synced(listOf(SyncedRoot(ROOT, "p", listOf(SyncedModule(":app", ROOT, setOf("build"), null))))) to EmptyReason.NotInstalled,
            SyncedProject.Synced(listOf(SyncedRoot(ROOT, "p", listOf(SyncedModule(":arch", ROOT, setOf("katachiTemplates"), "0.2.0"))))) to EmptyReason.Outdated("0.2.0"),
        )
        for ((project, reason) in cases) {
            synced = project
            val vm = viewModel()
            vm.dispatch(KatachiIntent.Opened)
            assertEquals(ScreenPhase.Empty(reason), vm.await { it.phase is ScreenPhase.Empty }.phase)
        }
        assertEquals(0, runner.requests.size)
    }

    @Test
    fun `同期が終わったら探し直して見つかれば読み込む`() = runBlocking {
        val found = synced
        synced = SyncedProject.NotSynced
        val vm = viewModel()
        vm.dispatch(KatachiIntent.Opened)
        assertEquals(ScreenPhase.Empty(EmptyReason.NotSynced), vm.await { it.phase is ScreenPhase.Empty }.phase)
        synced = found
        vm.dispatch(KatachiIntent.SyncCompleted)
        assertEquals(7, vm.await { it.phase == ScreenPhase.Ready && it.loading == null }.rows.size)
    }

    @Test
    fun `検索しても選択と入力は残りフッターの対象はチェック中の全行のまま`() = runBlocking {
        val vm = viewModel()
        vm.loaded()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.Search("usecase"))
        val searching = vm.state.value
        assertEquals(listOf(false, true), searchTemplates(searching.rows, searching.searchQuery, searching.form).map { it.isOutsideSearch }.sorted())
        assertNull(searching.generateBlocker)
        vm.dispatch(KatachiIntent.Search(""))
        assertEquals("User", vm.state.value.form.inputOf(FieldId(repository, "name")))
    }

    @Test
    fun `読み込みで想定外の例外が出ても読み込み中のまま残さずエラーにする`() = runBlocking {
        loadThrows = IllegalStateException("boom")
        val vm = viewModel()
        vm.dispatch(KatachiIntent.Opened)
        val error = vm.await { it.phase is ScreenPhase.LoadError }
        assertNull(error.loading)
        val details = ((error.phase as? ScreenPhase.LoadError)?.failure as? LoadFailure.Gradle)?.failure?.details
        assertEquals(listOf("IllegalStateException: boom"), details)
    }

    @Test
    fun `生成で想定外の例外が出ても生成中のまま残さず失敗の結果にする`() = runBlocking {
        generate = { throw IllegalStateException("boom") }
        val vm = viewModel()
        vm.loaded()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.Generate)
        val finished = vm.finished()
        val failed = finished.report.items.single().result as? GenerationItemResult.Failed
        assertEquals(GenerationFailure.NotReached(GradleFailure.Other(listOf("IllegalStateException: boom"))), failed?.failure)
        assertEquals("katachi: before generating (Repository)", finished.localHistoryLabel)
    }

    @Test
    fun `読み込みを待っている生成を止めるとフォームに戻り読み込み後も生成しない`() = runBlocking {
        fs.write(json, ContractFixtures.json("arch-a"))
        loadGate = CompletableDeferred()
        val vm = viewModel()
        vm.dispatch(KatachiIntent.Opened)
        runner.reachedGate.await()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.Generate)
        vm.dispatch(KatachiIntent.CancelGeneration)
        assertNull(vm.state.value.generation)

        loadGate?.complete(Unit)
        vm.await { it.loading == null }
        assertEquals(1, runner.requests.size)
        assertNull(vm.state.value.generation)
    }

    @Test
    fun `生成で書いたファイルは自分の書き込みとして定義の変更から外せる`() = runBlocking {
        val vm = viewModel()
        vm.loaded()
        vm.fillRepository()
        vm.dispatch(KatachiIntent.Generate)
        vm.finished()
        assertTrue(vm.isOwnWrite(data.resolve("UserRepository.kt")))
        assertFalse(vm.isOwnWrite(arch.directory.resolve("src/test/kotlin/Architecture.kt")))
    }

    @Test
    fun `パスに使えない文字を入力しても落ちずに既にある印を付けない`() = runBlocking {
        val vm = viewModel()
        vm.loaded()
        vm.fillRepository(name = "Us\u0000er")
        assertEquals("Us\u0000er", vm.state.value.form.inputOf(FieldId(repository, "name")))
        assertTrue(vm.state.value.view.existingPaths.isEmpty())
    }
}
