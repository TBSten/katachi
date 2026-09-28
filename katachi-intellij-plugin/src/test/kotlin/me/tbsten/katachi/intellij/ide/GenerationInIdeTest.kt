package me.tbsten.katachi.intellij.ide

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel
import me.tbsten.katachi.intellij.presentation.ScreenPhase
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

/** Generating through the real IDE effects: VFS, editors, Local History, the conflict dialog, notifications. */
internal class GenerationInIdeTest : KatachiIdeTestBase() {
    private val repository get() = TemplateId(ModuleId(root, ":arch-a"), "data.Repository")
    private val dataDir get() = root.resolve("data/src/main/kotlin/com/example/data")
    private val questions = Collections.synchronizedList(mutableListOf<ConflictQuestion>())

    private fun loadedViewModel(): KatachiToolWindowViewModel {
        val viewModel = installService()
        KatachiToolWindowFactory().createToolWindowContent(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))
        viewModel.waitFor("loaded") { it.phase == ScreenPhase.Ready && it.loading == null }
        viewModel.dispatch(KatachiIntent.ToggleCheck(repository))
        viewModel.dispatch(KatachiIntent.Input(FieldId(repository, "name"), "User"))
        return viewModel
    }

    private fun answerConflicts(choice: ConflictChoice) {
        ConflictDialogs.setTestAnswer({ question ->
            questions += question
            choice
        }, testRootDisposable)
    }

    private fun KatachiToolWindowViewModel.generateAndWait(): GenerationState.Finished {
        dispatch(KatachiIntent.Generate)
        return waitFor("generated") { it.generation is GenerationState.Finished }.generation as GenerationState.Finished
    }

    private fun resultOfRepository(finished: GenerationState.Finished): GenerationItemResult =
        finished.report.items.single { repository in it.templateIds }.result

    fun `test 生成したファイルが VFS に載り、最初の1ファイルがエディタで開く`() {
        val viewModel = loadedViewModel()

        val finished = viewModel.generateAndWait()

        val first = dataDir.resolve("UserRepository.kt")
        assertTrue(resultOfRepository(finished) is GenerationItemResult.Generated)
        assertNotNull(LocalFileSystem.getInstance().findFileByNioFile(first))
        assertNotNull(LocalFileSystem.getInstance().findFileByNioFile(dataDir.resolve("UserRepositoryImpl.kt")))
        assertEquals(listOf(first), openFiles())
    }

    fun `test 生成の前に Local History のラベルを打ち、その名前を結果に出す`() {
        val viewModel = loadedViewModel()

        val finished = viewModel.generateAndWait()

        // The label names the row's title (design draft section 6), which arch-a.json gives in Japanese.
        assertEquals(KatachiBundle.message("localHistory.label", "リポジトリ"), finished.localHistoryLabel)
    }

    fun `test 設定で開かないを選ぶと生成してもファイルを開かない`() {
        KatachiSettings.getInstance(project).openAfterGeneration = OpenAfterGeneration.None
        try {
            val viewModel = loadedViewModel()

            viewModel.generateAndWait()

            assertEmpty(openFiles())
        } finally {
            KatachiSettings.getInstance(project).openAfterGeneration = OpenAfterGeneration.First
        }
    }

    fun `test 衝突ダイアログで上書きを選ぶと同じテンプレートを overwrite でやり直して開く`() {
        writeExisting()
        answerConflicts(ConflictChoice.Overwrite)
        gradle.generations += listOf("conflict", "overwrite")
        val viewModel = loadedViewModel()

        val finished = viewModel.generateAndWait()

        assertEquals(listOf(dataDir.resolve("UserRepository.kt")), questions.single().existing)
        val runs = gradle.requests.filter { it.tasks.first().taskPath.endsWith(":katachiTemplate") }
        assertEquals(listOf("fail", "overwrite"), runs.map { run -> run.tasks.single().args.single { it.first == "onExisting" }.second })
        assertTrue(resultOfRepository(finished) is GenerationItemResult.Generated)
        assertEquals(listOf(dataDir.resolve("UserRepository.kt")), openFiles())
    }

    fun `test 衝突ダイアログで書かずに次へを選ぶと再実行せずスキップ扱いにする`() {
        writeExisting()
        answerConflicts(ConflictChoice.SkipAndContinue)
        gradle.generations += "conflict"
        val viewModel = loadedViewModel()

        val finished = viewModel.generateAndWait()

        assertTrue(resultOfRepository(finished) is GenerationItemResult.Skipped)
        assertEquals(1, gradle.requests.count { it.tasks.first().taskPath.endsWith(":katachiTemplate") })
        assertEmpty(openFiles())
    }

    fun `test 衝突ダイアログでここで止めると何も開かずに止まった結果になる`() {
        writeExisting()
        answerConflicts(ConflictChoice.Stop)
        gradle.generations += "conflict"
        val viewModel = loadedViewModel()

        val finished = viewModel.generateAndWait()

        assertTrue(resultOfRepository(finished) is GenerationItemResult.StoppedAtConflict)
        assertEmpty(openFiles())
    }

    fun `test Tool Window が隠れていれば生成の完了を通知する`() {
        val notifications = Collections.synchronizedList(mutableListOf<Notification>())
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    notifications += notification
                }
            },
        )
        val viewModel = loadedViewModel()

        viewModel.generateAndWait()

        waitUntil("a notification") { notifications.any { it.groupId == KatachiNotifications.GROUP_ID } }
        assertEquals(KatachiBundle.message("notification.generated", 2), notifications.first { it.groupId == KatachiNotifications.GROUP_ID }.content)
    }

    fun `test 生成中にプロジェクトを閉じると Gradle にキャンセルが届き、ファイルを開かない`() {
        val gate = CompletableDeferred<Unit>()
        gradle.gate = gate
        val viewModel = loadedViewModel()
        viewModel.dispatch(KatachiIntent.Generate)
        waitUntil("the build is running") { gradle.reachedGate.isCompleted }

        // What closing the project does to the service: its scope is cancelled.
        scope.cancel()

        waitUntil("the build is cancelled") { gradle.cancelled }
        assertEmpty(openFiles())
        assertFalse(Files.exists(dataDir.resolve("UserRepository.kt")))
    }

    private fun writeExisting(): Path {
        val existing = dataDir.resolve("UserRepository.kt")
        Files.createDirectories(existing.parent)
        Files.writeString(existing, "// already here\n")
        return existing
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching(what, { condition() }, 10)
    }
}
