package me.tbsten.katachi.intellij.data.generate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.presentation.IdeEffects
import me.tbsten.katachi.intellij.presentation.entry.EntryGenerationLedger
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGenerationCatalog
import me.tbsten.katachi.intellij.testing.FakeGenerationIdeEffects
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.editorFile
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.placementMatch
import me.tbsten.katachi.intellij.testing.rowOfTemplate
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.singleFileRequest
import me.tbsten.katachi.intellij.testing.underRoot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path
import java.time.Instant

/**
 * Two generations of the same file from two entries (V2 L6): between the first check and the ledger
 * saying "generating" the flow suspends (saving, creating directories, writing the provisional file),
 * and a second run must not get through in that window.
 */
class SingleFileGenerationRaceTest {
    private val fs = FakeFileSystem()
    private val fake = FakeGenerationIdeEffects(fs)
    private val ledger = EntryGenerationLedger()
    private val rows = rowsOf("sample-jvm-with-captures")
    private val controller = rows.rowOfTemplate("api.Controller")
    private val target = underRoot("src/main/kotlin/com/example/controller/user/UserController.kt")

    /** Holds the first `createDirectories` until [gate] completes; [reached] completes when it waits. */
    private val reached = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<Unit>()
    private val effects: IdeEffects = object : IdeEffects by fake {
        override suspend fun createDirectories(directory: Path): Boolean {
            if (!reached.isCompleted) {
                reached.complete(Unit)
                gate.await()
            }
            return fake.createDirectories(directory)
        }
    }

    private val catalog = FakeGenerationCatalog { _, _ ->
        GenerationCatalogReload.Reloaded(listOf(DescriptionSnapshot(module(), "0.3.0", rows.map { it.template }, Instant.EPOCH)), TemplatePlacementIndex.EMPTY)
    }
    private val runner = FakeGradleTaskRunner(fs) { _, _ ->
        FakeRun(
            lines = listOf("> Task :arch-a:katachiTemplate", "  [template] Generating 1 files under ${ROOT.toUri()}", "  [template] Wrote ${target.toUri()}", "BUILD SUCCESSFUL in 1s"),
            writes = mapOf(target to "class UserController\n"),
        )
    }
    private val generation = SingleFileGeneration(effects, runner, fs, catalog, ledger)

    private fun request() = singleFileRequest(
        placementMatch(controller.template, module()),
        editorFile(ROOT.relativize(target).toString()),
        listOf("resource" to "user", "name" to "User"),
        target,
    )

    // covers: 論点6
    @Test
    fun `同じファイルの生成が仮のファイルを書く前に2本目に来たら2本目は書かずに断る`() = runBlocking {
        val first = async(Dispatchers.Default) { generation.run(request()) }
        withTimeout(10_000) { reached.await() }

        assertEquals(TargetState.HasContent, generation.checkTarget(target))
        val second = generation.run(request())
        gate.complete(Unit)

        assertEquals(SingleFileGenerationResult.Refused(EntryGenerationRefusal.TargetHasContent(target)), second)
        assertEquals(SingleFileGenerationResult.Generated(listOf(target)), withTimeout(10_000) { first.await() })
        assertEquals(1, runner.requests.size)
        assertEquals(LedgerEntry.Succeeded(controller.id), ledger.entryOf(target))
    }
}
