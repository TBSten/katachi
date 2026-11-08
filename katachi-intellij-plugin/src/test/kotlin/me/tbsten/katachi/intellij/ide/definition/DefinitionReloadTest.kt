package me.tbsten.katachi.intellij.ide.definition

import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CompletableDeferred
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.LoadAnswer
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.StaleReason

/**
 * V4 of the definition's updates (design 4.8, decision U-1) as the sequences a user goes through: open,
 * edit and save the definition, wait, reload; save while Gradle runs; a reload that fails.
 */
internal class DefinitionReloadTest : DefinitionServiceTestBase() {
    private val serviceFile = "src/service/HealthService.kt"

    private fun roleNames(): List<String>? = definition.forPath(root.resolve(serviceFile))?.matches?.map { it.role.qualifiedName }

    /** Waits [millis] while the EDT runs. */
    private fun idle(millis: Long) {
        val until = System.currentTimeMillis() + millis
        PlatformTestUtil.waitWithEventsDispatching({ "" }, { System.currentTimeMillis() >= until }, 10)
    }

    override fun setUp() {
        super.setUp()
        KatachiSettings.getInstance(project).autoReloadOnSave = true
    }

    fun `test テストソースを保存すると古い印のまま前の答えを返し待ってから読み直して新しくなる`() {
        install()
        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// v1\n")
        loaded()
        assertEquals(listOf("Service"), roleNames())
        val gate = CompletableDeferred<Unit>()
        // The reload answers with another definition, where the file is no role's.
        gradle.loads += LoadAnswer(definitionJson = definitionJson("synthetic-definition-basic"), gate = gate)

        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// v2\n")
        val stale = waitState("stale") { (it as? DefinitionState.Ready)?.freshness is Freshness.Stale } as DefinitionState.Ready
        assertEquals(StaleReason.DefinitionChanged, (stale.freshness as Freshness.Stale).reason)
        assertEquals(listOf("Service"), roleNames())
        assertEquals(1, gradle.loadRequests.size)

        // Two seconds (shortened here) after the save, Gradle runs: Loading, still with the stale answer.
        val loading = waitState("loading") { it is DefinitionState.Loading } as DefinitionState.Loading
        assertEquals(stale, loading.previous)
        assertEquals(listOf("Service"), roleNames())
        gate.complete(Unit)

        val fresh = waitState("reloaded") { (it as? DefinitionState.Ready)?.freshness == Freshness.Fresh } as DefinitionState.Ready
        assertEquals(2, gradle.loadRequests.size)
        assertEquals(emptyList<String>(), roleNames())
        assertNotSame(stale.definitions, fresh.definitions)
        // Stale → Loading → Ready, each once.
        val after = history.dropWhile { it != stale }
        assertEquals(listOf(DefinitionState.Ready::class, DefinitionState.Loading::class, DefinitionState.Ready::class), after.map { it::class })
    }

    fun `test 続けて保存すれば最後の保存から待って1回だけ読み直す`() {
        install(autoReloadDelayMillis = 600)
        loaded()
        repeat(3) { n ->
            writeFile("arch/src/test/kotlin/ServiceRole.kt", "// v$n\n")
            idle(200)
        }
        waitState("reloaded") { (it as? DefinitionState.Ready)?.freshness == Freshness.Fresh }
        idle(800)
        assertEquals(2, gradle.loadRequests.size)
    }

    fun `test build スクリプトの変更は古い印だけで読み直さない`() {
        install()
        loaded()
        writeFile("arch/build.gradle.kts", "plugins { }\n")
        waitState("stale") { (it as? DefinitionState.Ready)?.freshness is Freshness.Stale }
        idle(900)
        assertEquals(1, gradle.loadRequests.size)
        assertFalse(projectService.isAutoReloadPending)
    }

    fun `test 設定で切れば保存しても読み直さない`() {
        KatachiSettings.getInstance(project).autoReloadOnSave = false
        install()
        loaded()
        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// v2\n")
        waitState("stale") { (it as? DefinitionState.Ready)?.freshness is Freshness.Stale }
        idle(900)
        assertEquals(1, gradle.loadRequests.size)
    }

    fun `test Gradle が走っている間の保存は終わってから読み直す`() {
        install()
        loaded()
        activity.externalStarted("a sync the user started")

        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// v2\n")
        waitState("stale") { (it as? DefinitionState.Ready)?.freshness is Freshness.Stale }
        idle(1_000)
        assertEquals(1, gradle.loadRequests.size)
        assertTrue(projectService.isAutoReloadPending)

        activity.externalEnded("a sync the user started")
        waitState("reloaded") { (it as? DefinitionState.Ready)?.freshness == Freshness.Fresh }
        assertEquals(2, gradle.loadRequests.size)
    }

    fun `test 読み直しが失敗すれば ReloadFailed で古い印の前の答えを返し次の成功で新しくなる`() {
        install()
        loaded()
        gradle.loads += LoadAnswer(fails = true)

        definition.refresh(RefreshReason.UserAction)
        val failed = waitState("failed") { ((it as? DefinitionState.Ready)?.freshness as? Freshness.Stale)?.reason is StaleReason.ReloadFailed }
        val reason = ((failed as DefinitionState.Ready).freshness as Freshness.Stale).reason as StaleReason.ReloadFailed
        assertTrue(reason.failure is LoadFailure.Gradle)
        assertEquals(listOf("Service"), roleNames())

        definition.refresh(RefreshReason.UserAction)
        waitState("reloaded") { (it as? DefinitionState.Ready)?.freshness == Freshness.Fresh }
        assertEquals(3, gradle.loadRequests.size)
    }

    fun `test 何も読んでいなければ保存しても読み込みを始めない`() {
        install()
        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// v2\n")
        idle(900)
        assertEquals(0, gradle.loadRequests.size)
        assertEquals(DefinitionState.NotLoaded, definition.state.value)
    }

    fun `test 未保存の編集を読み込みが自分で保存しても古い印も次の読み込みも起こさない`() {
        val path = root.resolve("arch/src/test/kotlin/ServiceRole.kt")
        val document = unsavedDocument(path, onDisk = "// disk\n", inMemory = "// typed in the editor\n")
        install()

        loaded()
        // The first load saved the editor before Gradle read the sources: the VFS event of that save is its own.
        idle(1_000)
        assertEquals(1, gradle.loadRequests.size)
        assertEquals("// typed in the editor\n", path.toFile().readText())
        assertEquals(1, history.count { it is DefinitionState.Loading })
        assertTrue(history.none { it is DefinitionState.Ready && it.freshness is Freshness.Stale })

        // ⟳ with another unsaved edit: one load, no second one chasing the save the first made.
        // The fake Gradle answers in a few milliseconds, faster than one poll of waitState: hold the load, so that
        // seeing Loading does not depend on who is faster (KATACHI-307).
        val gate = CompletableDeferred<Unit>()
        gradle.loads += LoadAnswer(gate = gate)
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { document.setText("// typed again\n") }
        definition.refresh(RefreshReason.UserAction)
        waitState("loading") { it is DefinitionState.Loading }
        gate.complete(Unit)
        waitState("reloaded") { it is DefinitionState.Ready && !projectService.viewModel.isLoadRunning }
        idle(1_000)
        assertEquals(2, gradle.loadRequests.size)
        assertEquals("// typed again\n", path.toFile().readText())
        assertEquals(Freshness.Fresh, (definition.state.value as DefinitionState.Ready).freshness)
        assertEquals(2, history.count { it is DefinitionState.Loading })
        assertTrue(history.none { it is DefinitionState.Ready && it.freshness is Freshness.Stale })
    }

    fun `test 読み込み中に定義を書き換えれば結果は古い印のまま出る`() {
        KatachiSettings.getInstance(project).autoReloadOnSave = false
        install()
        loaded()
        val gate = CompletableDeferred<Unit>()
        gradle.loads += LoadAnswer(gate = gate)

        definition.refresh(RefreshReason.UserAction)
        waitState("loading") { it is DefinitionState.Loading }
        // The user saves the definition while Gradle runs: what Gradle reads may be from before.
        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// v2\n")
        gate.complete(Unit)

        val after = waitState("reloaded") { it is DefinitionState.Ready && !projectService.viewModel.isLoadRunning } as DefinitionState.Ready
        assertEquals(StaleReason.DefinitionChanged, (after.freshness as Freshness.Stale).reason)
        assertEquals(2, gradle.loadRequests.size)

        // The next read that starts after the change is fresh.
        definition.refresh(RefreshReason.UserAction)
        waitState("fresh") { (it as? DefinitionState.Ready)?.freshness == Freshness.Fresh && gradle.loadRequests.size == 3 }
    }
}
