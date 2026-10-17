package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.data.placement.DirectoryLister
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** The tool window's ViewModel asks for the directory candidates of the capture fields on screen (design ide-review-screens 3.3). */
class KatachiToolWindowViewModelCandidatesTest {
    private val fs = FakeFileSystem()
    private val arch = module(":arch-a")
    private val viewModelRow = TemplateId(arch.id, "feature.ViewModel")
    private val deepRow = TemplateId(arch.id, "feature.Deep")
    private val feature = FieldId(viewModelRow, "feature")
    private val deepFeature = FieldId(deepRow, "feature")

    /** What the disk holds: the directories directly under each path. Reads are recorded in [reads]. */
    private val disk = HashMap<Path, List<String>>()
    private val reads = CopyOnWriteArrayList<Path>()
    private val enteredReads = CopyOnWriteArrayList<Path>()
    private val finishedReads = CopyOnWriteArrayList<Path>()

    /** The read of this directory waits for the gate (in flight) until it is completed. */
    @Volatile private var gated: Pair<Path, CompletableDeferred<Unit>>? = null

    private val lister = DirectoryLister { directory ->
        reads.add(directory)
        enteredReads.add(directory)
        gated?.takeIf { it.first == directory }?.let { (_, gate) -> runBlocking { gate.await() } }
        disk[directory].orEmpty().also { finishedReads.add(directory) }
    }

    private val synced = SyncedProject.Synced(
        listOf(SyncedRoot(ROOT, "project", listOf(SyncedModule(":arch-a", arch.directory, setOf("katachiInternalTemplatesJson"), "0.3.0")))),
    )
    private val runner = FakeGradleTaskRunner(fs) { _, _ -> FakeRun(writes = mapOf(arch.templateDescriptionJson to ContractFixtures.json("capture"))) }

    private fun CoroutineScope.viewModel() = KatachiToolWindowViewModel(
        scope = this,
        syncedProject = { synced },
        runner = runner,
        fileSystem = fs,
        effects = FakeIdeEffects(),
        candidateLister = lister,
        candidateSettle = {},
    )

    private suspend fun KatachiToolWindowViewModel.await(predicate: (KatachiScreenState) -> Boolean): KatachiScreenState =
        withTimeout(5_000) { state.first(predicate) }

    private suspend fun KatachiToolWindowViewModel.loaded(): KatachiToolWindowViewModel {
        dispatch(KatachiIntent.Opened)
        await { it.phase == ScreenPhase.Ready && it.loading == null }
        return this
    }

    @Test
    fun `capture の欄が出ると、そのディレクトリの候補が state に入る`() = runBlocking {
        disk[ROOT.resolve("feature")] = listOf("settings", "home")
        val vm = viewModel().loaded()
        assertTrue(vm.state.value.captureCandidates.isEmpty())

        vm.dispatch(KatachiIntent.ToggleCheck(viewModelRow))
        val state = vm.await { feature in it.captureCandidates }

        assertEquals(listOf("home", "settings"), state.captureCandidates.getValue(feature).values)
    }

    @Test
    fun `チェックしていない行の欄は読まず、チェックを外すと候補も捨てる`() = runBlocking {
        disk[ROOT.resolve("feature")] = listOf("home")
        val vm = viewModel().loaded()
        assertTrue(reads.isEmpty())

        vm.dispatch(KatachiIntent.ToggleCheck(viewModelRow))
        vm.await { feature in it.captureCandidates }
        vm.dispatch(KatachiIntent.ToggleCheck(viewModelRow))

        assertFalse(feature in vm.state.value.captureCandidates)
    }

    @Test
    fun `手前の値を変えると読みかけの古い要求は捨てられ、古い候補は一度も state に出ず新しい値の候補が入る`() = runBlocking {
        disk[ROOT.resolve("app/x")] = listOf("old")
        disk[ROOT.resolve("app/y")] = listOf("fresh")
        val vm = viewModel().loaded()
        vm.dispatch(KatachiIntent.ToggleCheck(deepRow))
        val gate = CompletableDeferred<Unit>()
        // Only x's read waits: it is in flight (past the settle) when y arrives.
        gated = ROOT.resolve("app/x") to gate
        val history = CopyOnWriteArrayList<Set<String>>()
        val watcher = launch(Dispatchers.Unconfined) { vm.state.collect { history += it.captureCandidates.values.flatMap { list -> list.values }.toSet() } }

        vm.dispatch(KatachiIntent.Input(FieldId(deepRow, "area"), "x"))
        withTimeout(5_000) { while (ROOT.resolve("app/x") !in enteredReads) delay(5) }
        vm.dispatch(KatachiIntent.Input(FieldId(deepRow, "area"), "y"))
        val state = vm.await { it.captureCandidates[deepFeature]?.values == listOf("fresh") }
        gate.complete(Unit)
        withTimeout(5_000) { while (ROOT.resolve("app/x") !in finishedReads) delay(5) }
        delay(100)
        watcher.cancel()

        assertEquals(listOf("fresh"), state.captureCandidates.getValue(deepFeature).values)
        assertEquals(listOf("fresh"), vm.state.value.captureCandidates.getValue(deepFeature).values)
        assertFalse("x の結果が届いた: $history", history.any { "old" in it })
    }

    @Test
    fun `Default の scope で別スレッドの再読み込みと Input が交差しても最後の入力の候補が残る`() {
        val workScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            runBlocking {
                repeat(20) { disk[ROOT.resolve("app/a$it")] = listOf("c$it") }
                val vm = workScope.viewModel().loaded()
                vm.dispatch(KatachiIntent.ToggleCheck(deepRow))
                val racer = thread { repeat(300) { vm.onFilesChanged() } }
                for (i in 0 until 20) vm.dispatch(KatachiIntent.Input(FieldId(deepRow, "area"), "a$i"))
                racer.join()

                vm.await { it.captureCandidates[deepFeature]?.values == listOf("c19") }
                delay(200)
                assertEquals(listOf("c19"), vm.state.value.captureCandidates.getValue(deepFeature).values)
            }
        } finally {
            workScope.cancel()
        }
    }

    @Test
    fun `ファイルが変わったら読み直す`() = runBlocking {
        disk[ROOT.resolve("feature")] = listOf("home")
        val vm = viewModel().loaded()
        vm.dispatch(KatachiIntent.ToggleCheck(viewModelRow))
        vm.await { feature in it.captureCandidates }

        disk[ROOT.resolve("feature")] = listOf("home", "profile")
        vm.onFilesChanged()
        val state = vm.await { it.captureCandidates[feature]?.values == listOf("home", "profile") }

        assertEquals(listOf("home", "profile"), state.captureCandidates.getValue(feature).values)
    }

    @Test
    fun `定義を読み直すと、出ている欄の候補を読み直す`() = runBlocking {
        disk[ROOT.resolve("feature")] = listOf("home")
        val vm = viewModel().loaded()
        vm.dispatch(KatachiIntent.ToggleCheck(viewModelRow))
        vm.await { feature in it.captureCandidates }
        val before = reads.size

        disk[ROOT.resolve("feature")] = listOf("home", "news")
        vm.dispatch(KatachiIntent.Reload)
        val state = vm.await { it.captureCandidates[feature]?.values == listOf("home", "news") }

        assertTrue(reads.size > before)
        assertEquals(listOf("home", "news"), state.captureCandidates.getValue(feature).values)
    }
}
