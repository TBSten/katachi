package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.data.generate.GenerationCatalogReload
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.presentation.ScreenPhase
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * The project's one Gradle runner (plan chapter 1 "一覧の共有"): the tool window, the entries and the
 * reload for a generation all go through `KatachiProjectService.gradleRunner`, one build at a time.
 */
internal class SharedGradleRunnerTest : EntryServiceTestBase() {
    private val module get() = KatachiModule(":arch-a", moduleDirOf(":arch-a"), root, "project")

    /** Counts the builds running at once; the first build waits on [gate] while it is set. */
    private class OverlapRunner(private val delegate: GradleTaskRunner) : GradleTaskRunner {
        val running = AtomicInteger()
        val mostAtOnce = AtomicInteger()

        @Volatile var gate: CompletableDeferred<Unit>? = null
        val reachedGate = CompletableDeferred<Unit>()

        override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
            mostAtOnce.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            try {
                gate?.let { gate ->
                    this.gate = null
                    reachedGate.complete(Unit)
                    gate.await()
                }
                return delegate.run(request, listener)
            } finally {
                running.decrementAndGet()
            }
        }
    }

    private fun gatedToolWindowLoad(): Pair<OverlapRunner, CompletableDeferred<Unit>> {
        val runner = OverlapRunner(gradle)
        val gate = CompletableDeferred<Unit>()
        runner.gate = gate
        installEntryService(runner)
        KatachiToolWindowFactory().createToolWindowContent(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))
        PlatformTestUtil.waitWithEventsDispatching({ "the tool window's load reaches Gradle" }, { runner.reachedGate.isCompleted }, 10)
        return runner to gate
    }

    private fun <T> Deferred<T>.awaitPumping(): T {
        PlatformTestUtil.waitWithEventsDispatching({ "finished" }, { isCompleted }, 10)
        return getCompleted()
    }

    // covers: 論点11
    fun `test ツールウィンドウの読み込みはサービスの runner を通るのでその間に来た Gradle は待たされる`() {
        val (runner, gate) = gatedToolWindowLoad()
        val request = GradleRunRequest(root, listOf(GradleTaskInvocation(":arch-a:${KatachiModule.TEMPLATES_JSON_TASK}")))

        // Undispatched: without the shared runner it would reach Gradle before launch returns.
        val other = scope.launch(start = CoroutineStart.UNDISPATCHED) { service.gradleRunner.run(request, object : GradleRunListener {}) }
        assertEquals(1, runner.running.get())

        gate.complete(Unit)
        PlatformTestUtil.waitWithEventsDispatching({ "both ran" }, { other.isCompleted }, 10)
        assertEquals(1, runner.mostAtOnce.get())
        assertEquals(2, gradle.loadRequests.size)
    }

    // covers: 論点11
    fun `test ツールウィンドウの読み込みと生成のための作り直しが同時に来ても Gradle は重ならない`() {
        val (runner, gate) = gatedToolWindowLoad()

        val reload = scope.async(start = CoroutineStart.UNDISPATCHED) { service.reloadForGeneration(module) }
        assertEquals(1, runner.running.get())
        gate.complete(Unit)

        val result = reload.awaitPumping()
        assertTrue(result.toString(), result is GenerationCatalogReload.Reloaded)
        assertEquals(1, runner.mostAtOnce.get())
        assertEquals(2, gradle.loadRequests.size)
    }

    // covers: 論点11
    fun `test 生成のための作り直しは戻る前にツールウィンドウの一覧と索引に載せる`() {
        installEntryService()
        service.ensureLoaded()
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        gradle.fixtureOf["arch-a"] = "arch-b"

        val result = scope.async { service.reloadForGeneration(module) }.awaitPumping() as GenerationCatalogReload.Reloaded

        val published = service.placementIndex.value as PlacementIndexState.Ready
        assertSame(result.index, published.index)
        assertEquals(result.snapshots, service.viewModel.state.value.snapshots)
        assertEquals(listOf("data.Repository"), service.viewModel.state.value.rows.map { it.template.roleName })
        assertEquals(ScreenPhase.Ready, service.viewModel.state.value.phase)
    }

    // covers: 論点11
    fun `test 生成のための作り直しが失敗したら理由を返して一覧はそのまま`() {
        installEntryService()
        service.ensureLoaded()
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        val before = service.viewModel.state.value.snapshots
        gradle.loads += LoadAnswer(fails = true)

        val result = scope.async { service.reloadForGeneration(module) }.awaitPumping()

        assertTrue(result.toString(), result is GenerationCatalogReload.Failed && result.failure is LoadFailure.Gradle)
        assertSame(before, service.viewModel.state.value.snapshots)
    }

    fun `test 自分が書くと届け出たファイルの変更では定義が変わった帯を出さない`() {
        // The directory is known to the VFS first: its creation would be a definition change of its own.
        val own = moduleDirOf(":arch-a").resolve("src/main/kotlin/Own.kt")
        Files.createDirectories(own.parent)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(own.parent) ?: throw AssertionError("not in VFS: ${own.parent}")
        installEntryService()
        service.ensureLoaded()
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()

        service.registerOwnWrite(own)
        Files.writeString(own, "// written by an entry\n")
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(own)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertFalse(service.viewModel.state.value.view.definitionChanged)

        // Any other file of the definition still counts, so the watcher did look.
        val other = moduleDirOf(":arch-a").resolve("src/main/kotlin/Other.kt")
        Files.writeString(other, "// v1\n")
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(other) ?: throw AssertionError("not in VFS: $other")
        WriteAction.runAndWait<Throwable> { VfsUtil.saveText(file, "// v2\n") }
        service.viewModel.waitFor("banner") { it.view.definitionChanged }
    }
}
