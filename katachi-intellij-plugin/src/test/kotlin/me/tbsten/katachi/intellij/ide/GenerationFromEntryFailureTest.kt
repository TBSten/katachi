package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.data.generate.EntryGenerationFailure
import me.tbsten.katachi.intellij.data.generate.EntryGenerationRefusal
import me.tbsten.katachi.intellij.data.generate.EntryIdeAction
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationResult
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.templatesOf
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import me.tbsten.katachi.intellij.testing.ContractFixtures
import java.nio.file.Files
import java.nio.file.Path

/**
 * How a generation from an entry refuses and fails in the IDE (issues 6, 16, decisions 2, 12, 14, 18):
 * nothing written over content, Gradle kept from a provisional file that changed, the reason as a
 * balloon, the IDE failing, and the project closing.
 */
internal class GenerationFromEntryFailureTest : GenerationFromEntryTestBase() {
    // covers: 論点6
    fun `test 中身のあるファイルには何も書かず理由を出す`() {
        loaded()
        Files.createDirectories(target.parent)
        Files.writeString(target, "class UserController\n")

        val result = generate()

        assertEquals(SingleFileGenerationResult.Refused(EntryGenerationRefusal.TargetHasContent(target)), result)
        assertEquals("class UserController\n", Files.readString(target))
        assertTrue(gradle.generationRequests.isEmpty())
        assertEquals(listOf(entryRefusalText(EntryGenerationRefusal.TargetHasContent(target))), balloonTexts())
    }

    // covers: 論点6
    fun `test 開いている空のファイルに未保存で class A を打った状態では何も書かず理由を出す`() {
        loaded()
        unsavedDocument(target, onDisk = "", inMemory = "class A")
        FileEditorManager.getInstance(project).openFile(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target)!!, true)

        val result = generate()

        assertEquals(SingleFileGenerationResult.Refused(EntryGenerationRefusal.TargetHasContent(target)), result)
        assertEquals("", Files.readString(target))
        assertEquals("class A", documentText(target))
        assertEquals(1, gradle.requests.size)
        assertEquals(1, balloonTexts().size)
    }

    // covers: 論点16
    fun `test JSON の作り直しの間に仮のファイルを未保存で編集したら Gradle を走らせず理由を出す`() {
        loaded(DuringReload(gradle) { typeInto(target, "class Typed\n") })

        val result = generate()

        assertEquals(SingleFileGenerationResult.Failed(EntryGenerationFailure.ChangedMeanwhile(target)), result)
        assertTrue(gradle.generationRequests.isEmpty())
        assertTrue(documentText(target).orEmpty().endsWith("class Typed\n"))
        assertEquals(listOf(entryFailureText(EntryGenerationFailure.ChangedMeanwhile(target))), balloonTexts())
    }

    // covers: 論点16
    fun `test JSON の作り直しの間に仮の中身を外から変えたら Gradle を走らせない`() {
        loaded(DuringReload(gradle) { Files.writeString(target, "class Other\n") })

        val result = generate()

        assertEquals(SingleFileGenerationResult.Failed(EntryGenerationFailure.ChangedMeanwhile(target)), result)
        assertTrue(gradle.generationRequests.isEmpty())
        assertEquals("class Other\n", Files.readString(target))
    }

    fun `test 作り直しでテンプレートが消えたら失敗を balloon で知らせ仮の中身を残す`() {
        loaded()
        gradle.loads += LoadAnswer(json = ContractFixtures.json("arch-b"))

        val result = generate()

        assertEquals(SingleFileGenerationResult.Failed(EntryGenerationFailure.TemplateGone), result)
        assertTrue(Files.readString(target).contains("katachiTemplate"))
        assertEquals(listOf(entryFailureText(EntryGenerationFailure.TemplateGone)), balloonTexts())
    }

    fun `test 作り直しで出力先が変わったら失敗を balloon で知らせる`() {
        loaded()
        gradle.loads += LoadAnswer(json = ContractFixtures.json(FIXTURE).replace("com/example/controller/", "com/example/api/"))

        val result = generate()

        assertTrue(result.toString(), result is SingleFileGenerationResult.Failed && result.failure is EntryGenerationFailure.TargetMoved)
        assertTrue(gradle.generationRequests.isEmpty())
        assertEquals(1, balloonTexts().size)
    }

    // covers: 論点16
    fun `test Gradle が失敗したら仮の中身と案内が残り理由を balloon で出す`() {
        loaded()
        gradle.plans += PlannedGeneration(fails = true)

        val result = generate()

        assertTrue(result.toString(), result is SingleFileGenerationResult.Failed && result.failure is EntryGenerationFailure.Katachi)
        assertTrue(Files.readString(target).contains("./gradlew :arch-a:katachiTemplate --arg template=api.Controller"))
        assertTrue(service.ledger.entryOf(target) is LedgerEntry.Failed)
        assertEquals(1, balloonTexts().size)
    }

    // covers: 論点16
    fun `test properties で Gradle が失敗した後に同じファイルでやり直せる`() {
        val json = ContractFixtures.json(FIXTURE).replace("Controller.kt", "Controller.properties")
        repeat(3) { gradle.loads += LoadAnswer(json = json) }
        loaded()
        val properties = controllerDir.resolve("user/UserController.properties")
        val template = templateOf("api.Controller")
        gradle.plans += PlannedGeneration(fails = true)
        gradle.plans += PlannedGeneration { mapOf(properties to "name=User\n") }

        val first = generate(request(properties, template))
        val second = generate(request(properties, template))

        assertTrue(first.toString(), first is SingleFileGenerationResult.Failed)
        assertEquals(SingleFileGenerationResult.Generated(listOf(properties)), second)
        assertEquals("name=User\n", Files.readString(properties))
    }

    // covers: 論点16
    fun `test 書き込みアクションが例外を投げても操作は失敗にならず balloon で知らせる`() {
        loaded()
        probe = { if (it == EntrySdkCall.WriteAction) throw IllegalStateException("the platform refused") }

        val result = generate()

        assertEquals(SingleFileGenerationResult.Failed(EntryGenerationFailure.Ide(EntryIdeAction.CREATE_DIRECTORIES)), result)
        assertFalse(Files.exists(target))
        assertEquals(listOf(entryFailureText(EntryGenerationFailure.Ide(EntryIdeAction.CREATE_DIRECTORIES))), balloonTexts())
    }

    // covers: 論点16
    fun `test エディタで開けなくても生成は続き開けなかったことを balloon で知らせる`() {
        loaded()
        gradle.plans += writes()
        probe = { if (it == EntrySdkCall.Editor) throw IllegalStateException("no editor") }

        val result = generate()

        assertEquals(SingleFileGenerationResult.Generated(listOf(target)), result)
        assertEquals(generated, Files.readString(target))
        assertEquals(listOf(KatachiBundle.message("generate.failed.open", target.fileName.toString())), balloonTexts())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun `test ProcessCanceledException は握らずに生成の呼び出し元へ伝わる`() {
        loaded()
        probe = { if (it == EntrySdkCall.WriteAction) throw ProcessCanceledException() }

        val running = service.generateFromEntry(request())
        waitUntil("the generation ends") { running.isCompleted }

        val thrown = running.getCompletionExceptionOrNull()
        assertTrue(thrown.toString(), generateSequence(thrown) { it.cause }.any { it is ProcessCanceledException })
        assertEmpty(balloons)
    }

    fun `test 生成中にプロジェクトを閉じると Gradle にキャンセルが届き仮の中身が残る`() {
        loaded()
        val gate = CompletableDeferred<Unit>()
        val plan = writes(gate = gate)
        gradle.plans += plan
        service.generateFromEntry(request())
        waitUntil("the build waits") { plan.reachedGate.isCompleted }

        // What closing the project does to the service: its scope is cancelled.
        scope.cancel()

        waitUntil("the build is cancelled") { gradle.cancelled }
        assertTrue(Files.readString(target).contains("katachiTemplate"))
        waitUntil("the ledger says failed") { service.ledger.entryOf(target) is LedgerEntry.Failed }
    }

    /** [spec] as the service loaded it. */
    private fun templateOf(spec: String): ModuleTemplate =
        templatesOf(service.viewModel.state.value.snapshots).first { it.template.template == spec }

    /** Types [text] at the end of [path]'s Document on the EDT, without saving: the user editing meanwhile. */
    private fun typeInto(path: Path, text: String) {
        ApplicationManager.getApplication().invokeAndWait {
            val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw AssertionError("not in VFS: $path")
            val document = FileDocumentManager.getInstance().getDocument(file) ?: throw AssertionError("no document: $path")
            WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, text) }
        }
    }

    /** Runs [meanwhile] before the second load (the reload for the generation) reaches [delegate]. */
    private class DuringReload(private val delegate: DiskGradleRunner, private val meanwhile: () -> Unit) : GradleTaskRunner {
        override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
            if (delegate.loadRequests.size == 1 && !request.tasks.single().taskPath.endsWith(":katachiTemplate")) meanwhile()
            return delegate.run(request, listener)
        }
    }
}
