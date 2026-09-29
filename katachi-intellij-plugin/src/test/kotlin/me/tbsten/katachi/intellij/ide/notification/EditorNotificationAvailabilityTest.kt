package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CompletableDeferred
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.PlacementUnavailableReason
import me.tbsten.katachi.intellij.ide.LoadAnswer
import java.util.concurrent.Future

/** When katachi cannot be used the notification stays away, and a failing SDK call never breaks the editor (issue 3, decisions 16, 17). */
internal class EditorNotificationAvailabilityTest : EditorNotificationTestBase() {

    // covers: 論点3
    fun `test 同期前は通知を出さずサービスも作らない`() {
        synced = SyncedProject.NotSynced

        assertNull(panelOf(open("feature/a/ui/Home.kt", "")))
        assertEquals(0, servicesCreated.get())
        assertTrue(gradle.requests.isEmpty())
    }

    // covers: 論点3
    fun `test katachi 未導入の同期データでは通知を出さず Gradle も走らせずサービスも作らない`() {
        synced = SyncedProject.Synced(listOf(SyncedRoot(root, "project", listOf(SyncedModule(":app", root.resolve("app"), setOf("build"), null)))))

        val editor = open("feature/a/ui/Home.kt", "")

        assertNull(panelOf(editor))
        assertEquals(0, servicesCreated.get())
        assertTrue(gradle.requests.toString(), gradle.requests.isEmpty())
    }

    // covers: 論点3
    fun `test JSON の生成に失敗したら通知を出さない`() {
        gradle.loads += LoadAnswer(fails = true)
        ports().service().ensureLoaded()
        waitForIndex("failed") { it is PlacementIndexState.Unavailable }
        settle()

        assertNull(panelOf(open("feature/a/ui/Home.kt", "")))
    }

    // covers: 論点3
    fun `test 読み込み中は通知を出さず読み込みが終わると開いているファイルに出る`() {
        val gate = CompletableDeferred<Unit>()
        serviceRunner = GatedRunner(gradle, gate)
        gradle.loads += LoadAnswer(NOTIFICATION_JSON)
        val editor = open("feature/a/ui/Home.kt", "")

        assertNull(panelOf(editor))
        assertEquals(PlacementIndexState.Loading, waitForIndex("loading") { it == PlacementIndexState.Loading })
        assertNull(panelOf(editor))

        gate.complete(Unit)
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        PlatformTestUtil.waitWithEventsDispatching({ "no panel after the load" }, { panelOf(editor) != null }, 10)
    }

    // covers: 論点3
    fun `test 利用者の操作なしの読み込みが OFF でキャッシュが無ければ通知を出さず Gradle も走らせない`() {
        setAutoLoad(false)

        val editor = open("feature/a/ui/Home.kt", "")
        settleNotifications()
        waitForIndex("unavailable") { it == PlacementIndexState.Unavailable(PlacementUnavailableReason.LoadingWithoutUserDisabled) }
        settle()

        assertNull(panelOf(editor))
        assertTrue(gradle.requests.toString(), gradle.requests.isEmpty())
    }

    // covers: 論点3
    fun `test 操作なしの読み込みを OFF から ON に戻すと次の判定で読み込みを始め通知が出る`() {
        setAutoLoad(false)
        gradle.loads += LoadAnswer(NOTIFICATION_JSON)
        val editor = open("feature/a/ui/Home.kt", "")
        settleNotifications()
        waitForIndex("unavailable") { it == PlacementIndexState.Unavailable(PlacementUnavailableReason.LoadingWithoutUserDisabled) }
        settle()
        assertNull(panelOf(editor))

        setAutoLoad(true)
        updateAll()

        waitForIndex("ready") { it is PlacementIndexState.Ready }
        PlatformTestUtil.waitWithEventsDispatching({ "no panel after the load" }, { panelOf(editor) != null }, 10)
        assertEquals(1, gradle.loadRequests.size)
    }

    // covers: 論点3
    fun `test 索引が無いときに開くと読み込みを始め終わると通知が出る`() {
        gradle.loads += LoadAnswer(NOTIFICATION_JSON)

        val editor = open("feature/a/ui/Home.kt", "")
        settleNotifications()

        waitForIndex("ready") { it is PlacementIndexState.Ready }
        PlatformTestUtil.waitWithEventsDispatching({ "no panel after the load" }, { panelOf(editor) != null }, 10)
        assertEquals(1, gradle.loadRequests.size)
    }

    // covers: 論点3
    fun `test dumb mode の間も通知が出る`() {
        loadIndex()

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            assertNotNull(panelOf(open("feature/a/ui/Home.kt", "")))
        }
    }

    // covers: 論点3
    fun `test 判定の途中で SDK 呼び出しが投げても通知は出ずエラーにもならない`() {
        loadIndex()
        onSettingsRead = { throw IllegalStateException("The settings could not be read (a test failure)") }

        val file = write("feature/a/ui/Home.kt", "")

        assertNull(inReadAction { provider.collectNotificationData(project, file) })
        assertNull(panelOf(open(file)))
    }

    // covers: 論点3
    fun `test 判定の途中の ProcessCanceledException は投げ直す`() {
        loadIndex()
        onSettingsRead = { throw ProcessCanceledException() }
        val file = write("feature/a/ui/Home.kt", "")

        val thrown = runCatching { inReadAction { provider.collectNotificationData(project, file) } }.exceptionOrNull()

        assertTrue("$thrown", thrown is ProcessCanceledException || thrown?.cause is ProcessCanceledException)
    }

    // covers: 論点18
    fun `test 読み取りのやり直しで何度判定しても読み込みは1回`() {
        gradle.loads += LoadAnswer(NOTIFICATION_JSON)
        val file = write("feature/a/ui/Home.kt", "")

        val workers: List<Future<*>> = List(4) {
            ApplicationManager.getApplication().executeOnPooledThread {
                repeat(25) { ReadAction.runBlocking<Throwable> { provider.collectNotificationData(project, file) } }
            }
        }
        PlatformTestUtil.waitWithEventsDispatching("the reads", { workers.all { it.isDone } }, 10)
        workers.forEach { it.get() }
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()

        assertEquals(1, servicesCreated.get())
        assertEquals(1, gradle.loadRequests.size)
    }

    /** Runs [block] on a pooled thread under a read lock, as the platform calls the provider; what it threw is rethrown here. */
    private fun <T> inReadAction(block: () -> T): T {
        // Caught inside: the pooled thread's wrapper swallows a ProcessCanceledException.
        val future = ApplicationManager.getApplication().executeOnPooledThread<Result<T>> { runCatching { ReadAction.computeBlocking<T, Throwable> { block() } } }
        PlatformTestUtil.waitWithEventsDispatching("read action", { future.isDone }, 10)
        return future.get().getOrThrow()
    }
}

/** [delegate], but a load waits for [gate] first: the index stays Loading until the test opens it. */
private class GatedRunner(private val delegate: GradleTaskRunner, private val gate: CompletableDeferred<Unit>) : GradleTaskRunner {
    override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
        gate.await()
        return delegate.run(request, listener)
    }
}
