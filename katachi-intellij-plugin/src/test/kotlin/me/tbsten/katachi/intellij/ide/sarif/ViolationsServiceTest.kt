package me.tbsten.katachi.intellij.ide.sarif

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.sarif.ViolationsStore
import me.tbsten.katachi.intellij.data.watch.FileChanges
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiNotifications
import me.tbsten.katachi.intellij.ide.PlannedCheck
import me.tbsten.katachi.intellij.ide.definition.DefinitionServiceTestBase
import me.tbsten.katachi.intellij.ide.definition.KatachiDefinitionService
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.testing.SarifResult
import me.tbsten.katachi.intellij.testing.sarifText
import java.nio.file.Files
import java.nio.file.Path

/**
 * V4 of the violations (design 4.7): SARIF written behind the IDE reaches the service through the VFS, the
 * project's changes make it stale, and `katachi.RunCheck` runs the check through the project's Gradle.
 */
internal class ViolationsServiceTest : DefinitionServiceTestBase() {
    private lateinit var violations: KatachiViolationsService
    private val missing = mutableListOf<String>()

    private val reports: Path get() = root.resolve("arch/build/reports/katachi")

    private fun installViolations() {
        install()
        loaded()
        val store = ViolationsStore(
            scope = scope,
            runner = projectService.gradleRunner,
            fileSystem = NioProjectFileSystem,
            modules = { projectService.viewModel.definitionModules },
            retryDelayMillis = 100,
            onSarifMissing = { missing += it.gradlePath },
        )
        violations = KatachiViolationsService(project, store)
        project.replaceService(KatachiViolationsService::class.java, violations, testRootDisposable)
    }

    private fun idle(millis: Long) {
        val until = System.currentTimeMillis() + millis
        PlatformTestUtil.waitWithEventsDispatching({ "" }, { System.currentTimeMillis() >= until }, 10)
    }

    private fun sarif(vararg uris: String) = sarifText(root, uris.map { SarifResult("UnexpectedFile", it) })

    /**
     * Gradle writes [text] behind the IDE's back; the VFS refresh delivers the event, as ExternalSystem's refresh does.
     * The directory is known to the VFS first, as an IDE that shows `build/` has it: the VFS reports a new child only
     * of a directory whose children it has loaded.
     */
    private fun gradleWrites(relative: String, text: String) {
        val path = reports.resolve(relative)
        Files.createDirectories(path.parent)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path.parent)?.children
        Files.writeString(path, text)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)?.let { VfsUtil.markDirtyAndRefresh(false, false, false, it) }
    }

    private fun waitViolations(what: String, condition: (ViolationsState) -> Boolean): ViolationsState {
        PlatformTestUtil.waitWithEventsDispatching({ "$what: ${violations.state.value}" }, { condition(violations.state.value) }, 10)
        return violations.state.value
    }

    fun `test SARIF が書かれると VFS 経由で読み forFile が違反を答え katachi-2 も併せて読む`() {
        installViolations()
        assertEquals(ViolationsState.NotRun, waitViolations("not run") { it == ViolationsState.NotRun })
        gradleWrites("katachi.sarif", sarif("src/service/Stray.kt"))
        gradleWrites("katachi-2.sarif", sarif("src/service/Other.kt"))

        val ready = waitViolations("ready") { (it as? ViolationsState.Ready)?.runs?.size == 2 } as ViolationsState.Ready
        assertFalse(ready.stale)
        val stray = writeFile("src/service/Stray.kt", "")
        assertEquals(listOf("katachi.UnexpectedFile"), violations.forFile(stray).map { it.ruleId })
    }

    fun `test src の変更で古くなり build の下の変更では古くならない`() {
        installViolations()
        gradleWrites("katachi.sarif", sarif("src/service/Stray.kt"))
        waitViolations("ready") { it is ViolationsState.Ready }

        gradleWrites("../../classes/kotlin/A.class", "binary")
        writeFile("arch/build/tmp/x.txt", "x")
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertFalse((violations.state.value as ViolationsState.Ready).stale)

        writeFile("src/service/Changed.kt", "class Changed\n")
        waitViolations("stale") { (it as? ViolationsState.Ready)?.stale == true }
    }

    fun `test 壊れた SARIF は読み直しても壊れていれば前の答えを持った Unreadable`() {
        installViolations()
        gradleWrites("katachi.sarif", sarif("src/service/Stray.kt"))
        val ready = waitViolations("ready") { it is ViolationsState.Ready }
        gradleWrites("katachi.sarif", "{ \"version\": \"2.1.0\", ")
        val unreadable = waitViolations("unreadable") { it is ViolationsState.Unreadable } as ViolationsState.Unreadable
        assertEquals(ready, unreadable.previous)
    }

    fun `test RunCheck の列 前の違反 削除 書きかけ 新しい違反 で NotRun に戻らず runningChecks を出入りする`() {
        installViolations()
        gradleWrites("katachi.sarif", sarif("src/service/Old.kt"))
        waitViolations("old") { it is ViolationsState.Ready }
        val gate = CompletableDeferred<Unit>()
        val check = PlannedCheck(sarif = sarif("src/service/New.kt"), fails = true, partialFirst = sarif("src/service/New.kt").take(50), gate = gate)
        gradle.checks += check
        val seen = mutableListOf<ViolationsState>()

        val arch = projectService.viewModel.definitionModules.single()
        val run = violations.runCheck(arch.id)
        runBlocking { withTimeout(10_000) { check.reachedGate.await() } }
        // The test task deleted the last report, and the watcher says so (a VFS deletion here wakes an unrelated
        // bundled plugin's listener that fails in the test IDE; FileChangesTest covers the deletion's classification).
        assertFalse(Files.exists(reports))
        violations.onFilesChanged(FileChanges(sarifChanged = setOf(arch.id)))
        violations.refresh()
        idle(300)
        assertEquals(setOf(arch.id), violations.runningChecks.value)
        seen += violations.state.value
        assertSame(run, violations.runCheck(arch.id))
        gate.complete(Unit)

        assertEquals(GradleRunOutcome.Failed, runBlocking { withTimeout(10_000) { run.await() } })
        seen += violations.state.value
        assertEquals(emptySet<Any>(), violations.runningChecks.value)
        assertEquals(1, gradle.checkRequests.size)
        assertEquals(listOf(":arch:test"), gradle.checkRequests.single().taskNames)
        assertTrue(seen.none { it == ViolationsState.NotRun })
        assertEquals(listOf("New.kt"), (violations.state.value as ViolationsState.Ready).runs.map { it.location?.path?.fileName.toString() })
        assertEquals(emptyList<String>(), missing)
    }

    fun `test 検査が成功しても SARIF が無ければ report sarif の案内を出す`() {
        installViolations()
        gradle.checks += PlannedCheck(sarif = null)
        val arch = projectService.viewModel.definitionModules.single()
        assertEquals(GradleRunOutcome.Succeeded, runBlocking { withTimeout(10_000) { violations.runCheck(arch.id).await() } })
        assertEquals(listOf(":arch"), missing)
        assertEquals(ViolationsState.NotRun, violations.state.value)
    }

    fun `test RunCheck の対象はファイルの定義モジュールで無ければ唯一の定義モジュール`() {
        installViolations()
        val definitionService = KatachiDefinitionService(projectService) { false }
        val arch = projectService.viewModel.definitionModules.single()
        val file = writeFile("src/service/HealthService.kt", "")
        assertEquals(arch.id, RunCheckAction.moduleToCheck(project, definitionService, file))
        assertEquals(arch.id, RunCheckAction.moduleToCheck(project, definitionService, null))
    }

    fun `test 本物の配線では定義モジュールが分かったときにディスクの SARIF を読む`() {
        val path = reports.resolve("katachi.sarif")
        Files.createDirectories(path.parent)
        Files.writeString(path, sarif("src/service/Stray.kt"))
        install()
        violations = KatachiViolationsService(project, scope)
        loaded()
        waitViolations("read at start") { (it as? ViolationsState.Ready)?.runs?.size == 1 }
    }

    fun `test サービスは報告のディレクトリを VFS に載せ 端末の Gradle が書き換えた SARIF の変更が届く`() {
        val path = reports.resolve("katachi.sarif")
        Files.createDirectories(path.parent)
        Files.writeString(path, sarif("src/service/Stray.kt"))
        install()
        violations = KatachiViolationsService(project, scope)
        project.replaceService(KatachiViolationsService::class.java, violations, testRootDisposable)
        waitViolations("read at start") { (it as? ViolationsState.Ready)?.runs?.size == 1 }

        // The VFS knows the report without anybody having asked for it: that is what makes its changes reported.
        val known = LocalFileSystem.getInstance().findFileByNioFile(path)
        assertNotNull("the service loads the report directory into the VFS", known)
        // A Gradle in a terminal rewrites it; the IDE's file watcher marks it dirty and the refresh reports the change.
        Files.writeString(path, sarif("src/service/Stray.kt", "src/service/Other.kt"))
        VfsUtil.markDirtyAndRefresh(false, false, false, known!!)
        waitViolations("rewritten") { (it as? ViolationsState.Ready)?.runs?.size == 2 }
    }

    fun `test 報告のディレクトリがまだ無くても build の中を VFS に載せ ディレクトリの作成が届く`() {
        install()
        loaded()
        violations = KatachiViolationsService(project, scope)
        project.replaceService(KatachiViolationsService::class.java, violations, testRootDisposable)
        idle(500)
        assertEquals(ViolationsState.NotRun, violations.state.value)
        val build = LocalFileSystem.getInstance().findFileByNioFile(root.resolve("arch/build"))
        assertNotNull("the nearest directory of the reports is in the VFS", build)

        // The first check in a terminal creates build/reports/katachi.
        Files.createDirectories(reports)
        Files.writeString(reports.resolve("katachi.sarif"), sarif("src/service/Stray.kt"))
        VfsUtil.markDirtyAndRefresh(false, false, true, build!!)
        waitViolations("ready") { it is ViolationsState.Ready }
    }

    private fun installForActions() {
        install()
        project.replaceService(KatachiDefinitionService::class.java, definition, testRootDisposable)
        violations = KatachiViolationsService(project, scope)
        project.replaceService(KatachiViolationsService::class.java, violations, testRootDisposable)
    }

    /** Find Action's `katachi.RunCheck`, with [file] selected in the editor if any. */
    private fun runCheckAction(file: VirtualFile? = null) {
        val action = ActionManager.getInstance().getAction(RunCheckAction.ID)
        val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).apply { file?.let { add(CommonDataKeys.VIRTUAL_FILE, it) } }.build()
        action.actionPerformed(TestActionEvent.createTestEvent(action, context))
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching({ what }, condition, 10)
    }

    fun `test 何も検出も読み込みもしていない状態から RunCheck を実行しても唯一の定義モジュールの検査を走らせる`() {
        installForActions()
        assertTrue(projectService.viewModel.definitionModules.isEmpty())
        gradle.checks += PlannedCheck(sarif = sarif("src/service/Stray.kt"), fails = true)

        runCheckAction()

        waitUntil("the check ran") { gradle.checkRequests.size == 1 }
        assertEquals(listOf(":arch:test"), gradle.checkRequests.single().taskNames)
        waitViolations("ready") { it is ViolationsState.Ready }
    }

    fun `test 定義モジュールが複数あってもファイルの置き場所から検査する対象を決める`() {
        synced = syncedWithDefinition(":arch", ":arch-b")
        installForActions()
        val file = writeFile("arch-b/src/test/kotlin/Rules.kt", "// rules\n")

        runCheckAction(file)

        waitUntil("the check ran") { gradle.checkRequests.size == 1 }
        assertEquals(listOf(":arch-b:test"), gradle.checkRequests.single().taskNames)
    }

    fun `test 対象を決められなければ balloon で伝え検査は走らせない`() {
        synced = syncedWithDefinition(":arch", ":arch-b")
        val notifications = java.util.Collections.synchronizedList(mutableListOf<Notification>())
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    notifications += notification
                }
            },
        )
        installForActions()

        runCheckAction()

        waitUntil("a balloon") { notifications.any { it.groupId == KatachiNotifications.GROUP_ID } }
        assertEquals(KatachiBundle.message("violations.checkTargetUnknown"), notifications.first { it.groupId == KatachiNotifications.GROUP_ID }.content)
        idle(300)
        assertEquals(0, gradle.checkRequests.size)
    }

    fun `test 2 つのアクションが登録されている`() {
        assertNotNull(ActionManager.getInstance().getAction(RunCheckAction.ID))
        assertNotNull(ActionManager.getInstance().getAction(me.tbsten.katachi.intellij.ide.RefreshDefinitionAction.ID))
    }
}
