package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.PlacementUnavailableReason
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.ScreenPhase
import me.tbsten.katachi.intellij.testing.ContractFixtures
import java.nio.file.Files

/** `KatachiProjectService.ensureLoaded()` as the New menu and the editor notification call it (issues 11, 18, decisions 16, 17). */
internal class EnsureLoadedTest : EntryServiceTestBase() {

    // covers: 論点11
    fun `test 最初の ensureLoaded でツールウィンドウなしに一覧を1回だけ読み込む`() {
        installEntryService()

        service.ensureLoaded()

        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        assertEquals(listOf(listOf(":arch-a:${KatachiModule.TEMPLATES_JSON_TASK}")), gradle.requests.map { it.taskNames })
    }

    // covers: 論点18
    fun `test 読み込み中と読み込み後に BGT から何度呼んでも読み込みは1回`() {
        installEntryService()

        ensureLoadedFromBackground()
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        ensureLoadedFromBackground()
        service.ensureLoaded()
        settle()

        assertEquals(1, gradle.loadRequests.size)
    }

    // covers: 論点11
    fun `test 入口で読み込んだ後にツールウィンドウを開いても読み込み直さず同じ一覧を出す`() {
        installEntryService()
        service.ensureLoaded()
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        val loaded = service.viewModel.state.value.snapshots

        KatachiToolWindowFactory().createToolWindowContent(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))
        settle()

        assertEquals(1, gradle.loadRequests.size)
        assertEquals(ScreenPhase.Ready, service.viewModel.state.value.phase)
        assertSame(loaded, service.viewModel.state.value.snapshots)
    }

    // covers: 論点11
    fun `test 利用者の操作なしの読み込みが OFF なら ensureLoaded でも同期の完了でも Gradle を走らせない`() {
        setAutoLoad(false)
        installEntryService()

        service.ensureLoaded()
        settle()
        publishImportFinished()
        settle()

        assertTrue(gradle.requests.toString(), gradle.requests.isEmpty())
        assertEquals(
            PlacementIndexState.Unavailable(PlacementUnavailableReason.LoadingWithoutUserDisabled),
            waitForIndex("unavailable") { it is PlacementIndexState.Unavailable },
        )
    }

    // covers: 論点11
    fun `test OFF でもキャッシュの JSON があれば Gradle なしで索引が Ready になる`() {
        setAutoLoad(false)
        val json = moduleDirOf(":arch-a").resolve(KatachiModule.TEMPLATE_DESCRIPTION_JSON)
        Files.createDirectories(json.parent)
        Files.writeString(json, ContractFixtures.json("arch-a"))
        installEntryService()

        service.ensureLoaded()

        waitForIndex("ready from the cache") { it is PlacementIndexState.Ready }
        settle()
        assertTrue(gradle.requests.toString(), gradle.requests.isEmpty())
    }

    // covers: 論点11
    fun `test このセッションで読み込んだ後は同期の完了で定義モジュールが増えれば読み直す`() {
        installEntryService()
        service.ensureLoaded()
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()

        synced = syncedWith(":arch-a", ":arch-b")
        publishImportFinished()

        PlatformTestUtil.waitWithEventsDispatching({ "reloaded: ${gradle.requests}" }, { gradle.loadRequests.size == 2 }, 10)
        settle()
        assertEquals(listOf(":arch-a", ":arch-b"), service.viewModel.state.value.snapshots.map { it.module.gradlePath })
    }

    // covers: 論点18
    fun `test katachi の定義モジュールが無い同期データでは Gradle を走らせず索引は読み込み中にならず Unavailable`() {
        synced = SyncedProject.Synced(listOf(SyncedRoot(root, "project", listOf(SyncedModule(":app", root.resolve("app"), setOf("build"), null)))))
        installEntryService()

        service.ensureLoaded()

        assertEquals(
            PlacementIndexState.Unavailable(PlacementUnavailableReason.NoDefinition),
            waitForIndex("unavailable") { it is PlacementIndexState.Unavailable },
        )
        settle()
        assertTrue(gradle.requests.isEmpty())
        assertFalse(notificationUpdates.toString(), notificationUpdates.any { it == PlacementIndexState.Loading })
    }

    // covers: 論点11
    fun `test Gradle のインポート中に来た ensureLoaded はインポートが終わってから1回だけ走る`() {
        importing = true
        installEntryService()

        service.ensureLoaded()
        ensureLoadedFromBackground(threads = 2, times = 10)
        settle()
        assertTrue(gradle.requests.toString(), gradle.requests.isEmpty())
        assertEquals(PlacementIndexState.NotLoaded, service.placementIndex.value)

        importing = false
        publishImportFinished()

        waitForIndex("ready after the import") { it is PlacementIndexState.Ready }
        settle()
        assertEquals(1, gradle.loadRequests.size)
    }

    // covers: 論点11
    fun `test インポートの開始を聞いたサービスはその終わりまで読み込みを待つ`() {
        installEntryService()
        publishImportStarted()

        service.ensureLoaded()
        settle()
        assertTrue(gradle.requests.toString(), gradle.requests.isEmpty())

        publishImportFinished()

        waitForIndex("ready after the import") { it is PlacementIndexState.Ready }
        settle()
        assertEquals(1, gradle.loadRequests.size)
    }

    // covers: 論点11
    fun `test 入口がサービスを作った場合も定義の変更で読み直す`() {
        setAutoReloadOnSave(true)
        val source = moduleDirOf(":arch-a").resolve("src/test/kotlin/Architecture.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, "// v1\n")
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source) ?: throw AssertionError("not in VFS: $source")
        installEntryService()
        service.ensureLoaded()
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()

        WriteAction.runAndWait<Throwable> { VfsUtil.saveText(file, "// v2\n") }

        PlatformTestUtil.waitWithEventsDispatching({ "reloaded: ${gradle.requests}" }, { gradle.loadRequests.size == 2 }, 10)
    }
}
