package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.load.LoadResult
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.PlacementUnavailableReason
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeIdeEffects
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.module
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list loaded once for the tool window and the entries (issues 11, 18, decision 16), played on a
 * [ManualDispatcher] so that "nothing more ran" is checked after every queued task, without waiting.
 */
class EnsureLoadedViewModelTest {
    private val dispatcher = ManualDispatcher()
    private val scope = CoroutineScope(dispatcher + Job())
    private val fs = FakeFileSystem()
    private val effects = FakeIdeEffects()
    private val archA = module(":arch-a")
    private val archB = module(":arch-b")
    private var synced: SyncedProject = syncedWith(archA)
    private var autoLoad = true
    private var loadGate: CompletableDeferred<Unit>? = null

    /** Writes each requested module's fixture JSON, after [loadGate] when set. */
    private val runner = FakeGradleTaskRunner(fs) { request, _ -> FakeRun(writes = writesOf(request), gate = loadGate) }
    private val vm = KatachiToolWindowViewModel(scope, { synced }, runner, fs, effects, ioDispatcher = dispatcher, loadsWithoutUser = { autoLoad })

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun syncedWith(vararg modules: KatachiModule): SyncedProject = SyncedProject.Synced(
        listOf(SyncedRoot(ROOT, "project", modules.map { SyncedModule(it.gradlePath, it.directory, setOf(KatachiModule.TEMPLATES_JSON_TASK), "0.3.0") })),
    )

    private fun writesOf(request: GradleRunRequest): Map<java.nio.file.Path, String> = request.tasks.associate { invocation ->
        val module = module(invocation.taskPath.substringBeforeLast(':'))
        module.templateDescriptionJson to ContractFixtures.json(module.directory.fileName.toString())
    }

    private fun send(vararg intents: KatachiIntent) {
        intents.forEach(vm::dispatch)
        dispatcher.runAll()
    }

    private fun indexState(): PlacementIndexState =
        placementIndexStateOf(PlacementIndexSource.of(vm.state.value, vm.loadStarted.value)) { TemplatePlacementIndex.EMPTY }

    // covers: 論点11
    @Test
    fun `最初の EnsureLoaded で同期データから定義を見つけて一覧を1回だけ読み込む`() {
        send(KatachiIntent.EnsureLoaded)

        assertEquals(listOf(listOf(":arch-a:katachiInternalTemplatesJson")), runner.requests.map { it.taskNames })
        assertEquals(ScreenPhase.Ready, vm.state.value.phase)
        assertTrue(vm.loadStarted.value)
        assertTrue(indexState() is PlacementIndexState.Ready)
    }

    // covers: 論点18
    @Test
    fun `読み込み中と読み込み後に EnsureLoaded が何度来ても読み込みは増えない`() {
        val gate = CompletableDeferred<Unit>()
        loadGate = gate
        send(KatachiIntent.EnsureLoaded)
        assertTrue(runner.reachedGate.isCompleted)
        assertEquals(PlacementIndexState.Loading, indexState())

        repeat(5) { send(KatachiIntent.EnsureLoaded) }
        gate.complete(Unit)
        dispatcher.runAll()
        repeat(5) { send(KatachiIntent.EnsureLoaded) }

        assertEquals(1, runner.requests.size)
        assertFalse(vm.isLoadRunning)
    }

    // covers: 論点11
    @Test
    fun `EnsureLoaded で読み込んだ後にツールウィンドウを開いても読み込み直さずその一覧を出す`() {
        send(KatachiIntent.EnsureLoaded)
        val loaded = vm.state.value

        send(KatachiIntent.Opened, KatachiIntent.Opened)

        assertEquals(1, runner.requests.size)
        assertEquals(loaded.snapshots, vm.state.value.snapshots)
        assertEquals(ScreenPhase.Ready, vm.state.value.phase)
    }

    // covers: 論点11
    @Test
    fun `ツールウィンドウで読み込んだ後の EnsureLoaded は何もしない`() {
        send(KatachiIntent.Opened)

        send(KatachiIntent.EnsureLoaded, KatachiIntent.EnsureLoaded)

        assertEquals(1, runner.requests.size)
    }

    // covers: 論点11
    @Test
    fun `利用者の操作なしの読み込みが OFF なら EnsureLoaded でも同期の完了でも Gradle を走らせない`() {
        autoLoad = false

        send(KatachiIntent.EnsureLoaded, KatachiIntent.EnsureLoaded)
        synced = syncedWith(archA, archB)
        send(KatachiIntent.SyncCompleted)

        assertTrue(runner.requests.isEmpty())
        assertFalse(vm.loadStarted.value)
        assertEquals(PlacementIndexState.Unavailable(PlacementUnavailableReason.LoadingWithoutUserDisabled), indexState())
    }

    // covers: 論点11
    @Test
    fun `OFF でもキャッシュの JSON があればそれだけで一覧と索引ができる`() {
        autoLoad = false
        fs.write(archA.templateDescriptionJson, ContractFixtures.json("arch-a"))

        send(KatachiIntent.EnsureLoaded)

        assertTrue(runner.requests.isEmpty())
        assertTrue(vm.state.value.snapshots.isNotEmpty())
        assertTrue(indexState() is PlacementIndexState.Ready)
    }

    // covers: 論点11
    @Test
    fun `OFF で探しただけの後でもツールウィンドウを開けば利用者の操作として読み込む`() {
        autoLoad = false
        send(KatachiIntent.EnsureLoaded)

        send(KatachiIntent.Opened)

        assertEquals(1, runner.requests.size)
        assertEquals(ScreenPhase.Ready, vm.state.value.phase)
    }

    // covers: 論点11
    // The entries' half of V2 M3: they must send EnsureLoaded again while the index says
    // LoadingWithoutUserDisabled, and the ViewModel then loads with the setting turned back on.
    @Test
    fun `OFF で探しただけの後に ON へ戻せば次の EnsureLoaded で読み込んで索引が使えるようになる`() {
        autoLoad = false
        send(KatachiIntent.EnsureLoaded)
        assertEquals(PlacementIndexState.Unavailable(PlacementUnavailableReason.LoadingWithoutUserDisabled), indexState())

        autoLoad = true
        send(KatachiIntent.EnsureLoaded)

        assertEquals(1, runner.requests.size)
        assertTrue(vm.loadStarted.value)
        assertTrue(indexState() is PlacementIndexState.Ready)
    }

    // covers: 論点11
    @Test
    fun `一度読み込んだ後の同期の完了で定義モジュールが増えていれば読み直す`() {
        send(KatachiIntent.EnsureLoaded)
        synced = syncedWith(archA, archB)

        send(KatachiIntent.SyncCompleted)

        assertEquals(2, runner.requests.size)
        assertEquals(listOf(":arch-a", ":arch-b"), vm.state.value.snapshots.map { it.module.gradlePath })
    }

    // covers: 論点11
    @Test
    fun `まだ誰も読み込んでいなければ同期の完了では読み込まない`() {
        send(KatachiIntent.SyncCompleted)

        assertTrue(runner.requests.isEmpty())
        assertEquals(PlacementIndexState.NotLoaded, indexState())
    }

    // covers: 論点18
    @Test
    fun `katachi の定義モジュールが無い同期データでは Gradle を走らせず索引は一度も読み込み中にならない`() {
        synced = SyncedProject.Synced(listOf(SyncedRoot(ROOT, "project", listOf(SyncedModule(":app", ROOT.resolve("app"), setOf("build"), null)))))
        val seen = mutableListOf<PlacementIndexState>()
        dispatcher.afterTask = { seen += indexState() }

        send(KatachiIntent.EnsureLoaded)

        assertTrue(runner.requests.isEmpty())
        assertEquals(PlacementIndexState.Unavailable(PlacementUnavailableReason.NoDefinition), indexState())
        assertFalse(seen.toString(), seen.any { it == PlacementIndexState.Loading })
    }

    // covers: 論点11
    @Test
    fun `生成のための作り直しはその定義だけを読み直してほかの定義の一覧を残す`() {
        synced = syncedWith(archA, archB)
        send(KatachiIntent.EnsureLoaded)
        var result: LoadResult? = null

        scope.launch { result = vm.reloadForGeneration(archB) }
        dispatcher.runAll()

        assertTrue(result is LoadResult.Loaded)
        assertEquals(listOf(":arch-b:katachiInternalTemplatesJson"), runner.requests.last().taskNames)
        assertEquals(listOf(":arch-a", ":arch-b"), vm.state.value.snapshots.map { it.module.gradlePath })
    }

    // covers: 論点11
    @Test
    fun `生成のための作り直しは走っている読み込みが終わってから Gradle を走らせる`() {
        val gate = CompletableDeferred<Unit>()
        loadGate = gate
        send(KatachiIntent.EnsureLoaded)
        var result: LoadResult? = null

        scope.launch { result = vm.reloadForGeneration(archA) }
        dispatcher.runAll()
        assertEquals(1, runner.requests.size)
        assertEquals(null, result)

        loadGate = null
        gate.complete(Unit)
        dispatcher.runAll()
        assertEquals(2, runner.requests.size)
        assertTrue(result is LoadResult.Loaded)
    }

    @Test
    fun `入口の生成が書くと届け出たファイルは定義の変更と見なさない`() {
        val target = ROOT.resolve("arch-a/src/main/kotlin/Generated.kt")
        assertFalse(vm.isOwnWrite(target))

        vm.registerOwnWrite(target)

        assertTrue(vm.isOwnWrite(target))
    }
}
