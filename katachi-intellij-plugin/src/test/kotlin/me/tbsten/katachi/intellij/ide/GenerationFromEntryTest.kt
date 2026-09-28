package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.CompletableDeferred
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationResult
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import me.tbsten.katachi.intellij.testing.ContractFixtures
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * The generation from an entry end to end in the IDE (plan chapter 1 "生成の流れ", issues 1, 2, 11, 16):
 * what is written, opened and saved first, what the editor shows afterwards, and that it shares the
 * tool window's list and Gradle.
 */
internal class GenerationFromEntryTest : GenerationFromEntryTestBase() {
    // covers: 論点16
    fun `test 仮のファイルが Gradle より先に書かれて開き保存され上書き後はエディタの中身が生成物になる`() {
        addSourceRoot(sourceRoot)
        loaded()
        val gate = CompletableDeferred<Unit>()
        val plan = writes(gate = gate)
        gradle.plans += plan

        val running = service.generateFromEntry(request())
        waitUntil("the build waits") { plan.reachedGate.isCompleted }

        val provisional = Files.readString(target)
        assertTrue(provisional, provisional.startsWith("package com.example.controller.user\n\n// "))
        assertTrue(provisional, provisional.contains("--arg template=api.Controller --arg onExisting=overwrite"))
        assertTrue(openFiles().toString(), target in openFiles())
        assertEquals(provisional, documentText(target))
        assertFalse(isUnsaved(target))
        assertTrue(service.ledger.entryOf(target) is LedgerEntry.Generating)

        gate.complete(Unit)
        assertEquals(SingleFileGenerationResult.Generated(listOf(target)), running.awaitPumping())
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(generated, documentText(target))
        assertEquals(LedgerEntry.Succeeded(controller().id), service.ledger.entryOf(target))
    }

    // covers: 論点2
    fun `test Gradle は JSON の作り直しの後に template 1つと onExisting=overwrite で走る`() {
        loaded()
        gradle.plans += writes()

        generate()

        val kinds = gradle.requests.map { if (it.tasks.single().taskPath.endsWith(":katachiTemplate")) "generate" else "json" }
        assertEquals(listOf("json", "json", "generate"), kinds)
        val args = gradle.generationRequests.single().tasks.single().args
        assertEquals(listOf("template" to "api.Controller", "onExisting" to "overwrite", "resource" to "user", "name" to "User"), args)
    }

    // covers: 論点1
    fun `test まだ無い途中のディレクトリを作りパッケージ名は実在する祖先に残りのセグメントを足す`() {
        addSourceRoot(sourceRoot)
        loaded()
        gradle.plans += PlannedGeneration(fails = true)

        generate()

        assertTrue(Files.isDirectory(target.parent))
        assertTrue(Files.readString(target).startsWith("package com.example.controller.user\n"))
    }

    // covers: 論点16
    fun `test ソースルートの外では package 行を書かない`() {
        loaded()
        gradle.plans += PlannedGeneration(fails = true)

        generate()

        val provisional = Files.readString(target)
        assertFalse(provisional, provisional.contains("package"))
        assertTrue(provisional, provisional.startsWith("// katachi: "))
    }

    // covers: 論点16
    fun `test json の仮の中身は空で properties は # のコメントになる`() {
        loaded()
        val json = controllerDir.resolve("user/User.json")
        val properties = controllerDir.resolve("user/User.properties")

        // Neither is where api.Controller writes: the reload finds the target moved and stops, leaving the provisional content.
        generate(request(json))
        generate(request(properties))

        assertEquals("", Files.readString(json))
        val lines = Files.readString(properties).lines().filter { it.isNotEmpty() }
        assertTrue(lines.toString(), lines.isNotEmpty() && lines.all { it.startsWith("# ") })
        assertTrue(gradle.generationRequests.isEmpty())
    }

    // covers: 論点16
    fun `test 入口からの生成は開く設定が None でも仮のファイルを開く`() {
        val settings = KatachiSettings.getInstance(project)
        settings.openAfterGeneration = OpenAfterGeneration.None
        try {
            loaded()
            gradle.plans += writes()

            generate()

            assertEquals(listOf(target), openFiles())
        } finally {
            settings.openAfterGeneration = OpenAfterGeneration.First
        }
    }

    // covers: 論点11
    fun `test 定義を変えた直後の生成は作り直した定義を使い生成の後は索引とツールウィンドウも新しい定義になる`() {
        loaded()
        val renamed = ContractFixtures.json(FIXTURE).replace("\"コントローラ\"", "\"新しいコントローラ\"")
        gradle.loads += LoadAnswer(json = renamed)
        gradle.plans += writes()

        generate()

        assertTrue(service.viewModel.state.value.rows.any { it.template.title == "新しいコントローラ" })
        val index = (service.placementIndex.value as PlacementIndexState.Ready).index
        val match = index.matchesForFile(target).single()
        assertEquals("新しいコントローラ", match.template.template.title)
        assertEquals(generated, Files.readString(target))
        val kinds = gradle.requests.map { if (it.tasks.single().taskPath.endsWith(":katachiTemplate")) "generate" else "json" }
        assertEquals(listOf("json", "json", "generate"), kinds)
    }

    // covers: 論点11
    fun `test ツールウィンドウの読み込み中に通知から生成しても Gradle は重ならない`() {
        val runner = CountingRunner(gradle)
        install(runner)
        gradle.plans += writes()
        KatachiToolWindowFactory().createToolWindowContent(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))
        waitUntil("the tool window's load reaches Gradle") { runner.reachedGate.isCompleted }

        val running = service.generateFromEntry(request())
        waitUntil("the provisional file is written") { Files.exists(target) }
        assertEquals(1, runner.running.get())
        runner.gate.complete(Unit)

        assertEquals(SingleFileGenerationResult.Generated(listOf(target)), running.awaitPumping())
        assertEquals(1, runner.mostAtOnce.get())
        assertEquals(3, gradle.requests.size)
    }

    fun `test 仮のファイルと途中のディレクトリを書いても定義が変わったことにならない`() {
        loaded()
        gradle.plans += writes()

        generate()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertFalse(service.viewModel.state.value.view.definitionChanged)

        // The same src/ still counts for any other file, so the watcher did look there.
        val other = controllerDir.resolve("Other.kt")
        Files.writeString(other, "// v1\n")
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(other) ?: throw AssertionError("not in VFS: $other")
        WriteAction.runAndWait<Throwable> { VfsUtil.saveText(file, "// v2\n") }
        service.viewModel.waitFor("banner") { it.view.definitionChanged }
    }

    /** Holds the first build on [gate] and counts the builds running at once. */
    private class CountingRunner(private val delegate: GradleTaskRunner) : GradleTaskRunner {
        val running = AtomicInteger()
        val mostAtOnce = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val reachedGate = CompletableDeferred<Unit>()
        private val first = AtomicInteger()

        override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
            mostAtOnce.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            try {
                if (first.getAndIncrement() == 0) {
                    reachedGate.complete(Unit)
                    gate.await()
                }
                return delegate.run(request, listener)
            } finally {
                running.decrementAndGet()
            }
        }
    }
}
