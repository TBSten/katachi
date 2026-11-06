package me.tbsten.katachi.intellij.ide.mcp

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.ide.LoadAnswer
import me.tbsten.katachi.intellij.ide.PlannedCheck
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.definition.DefinitionServiceTestBase
import me.tbsten.katachi.intellij.ide.definition.KatachiDefinitionService
import me.tbsten.katachi.intellij.ide.sarif.KatachiViolationsService
import me.tbsten.katachi.intellij.testing.SarifResult
import me.tbsten.katachi.intellij.testing.sarifText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** katachi_refresh_definition and katachi_run_check over the real project, definition and violations services and a fake Gradle. */
internal class KatachiMcpGradleToolsTest : DefinitionServiceTestBase() {
    private lateinit var violations: KatachiViolationsService

    private fun installAll() {
        install()
        project.replaceService(KatachiDefinitionService::class.java, definition, testRootDisposable)
        violations = KatachiViolationsService(project, scope)
        project.replaceService(KatachiViolationsService::class.java, violations, testRootDisposable)
    }

    private fun tools(refreshTimeout: Duration = KatachiMcpTools.REFRESH_TIMEOUT, checkTimeout: Duration = KatachiMcpTools.CHECK_TIMEOUT) = KatachiMcpTools(
        DefinitionPorts({ true }, { true }, { definition }, { violations }),
        projectBase = root,
        gradle = ProjectGradleActions(project),
        refreshTimeout = refreshTimeout,
        checkTimeout = checkTimeout,
    )

    /** Off the EDT while the EDT keeps dispatching: a reload and the save before a check hop to it. */
    private fun <T> call(tools: KatachiMcpTools = tools(), block: suspend (KatachiMcpTools) -> T): Result<T> {
        val running = scope.async { runCatching { block(tools) } }
        PlatformTestUtil.waitWithEventsDispatching({ "the tool call did not return" }, { running.isCompleted }, 20)
        return runBlocking { running.await() }
    }

    private fun sarif(vararg uris: String) = sarifText(root, uris.map { SarifResult("UnexpectedFile", it) })

    fun `test refresh は読み込みを走らせて終わるまで待ち 読み込み後の状態を返す`() {
        installAll()
        loaded()
        val result = call { it.refreshDefinition(null) }.getOrThrow()
        assertEquals(2, gradle.loadRequests.size)
        assertEquals("ready", result.state)
        assertEquals(listOf(":arch"), result.modules)
        assertFalse(result.stale)
        assertNull(result.loadFailure)
        assertEquals(emptyList<Any>(), result.definitionErrors)
    }

    fun `test 未読み込みからでも refresh で読める`() {
        installAll()
        val result = call { it.refreshDefinition(":arch") }.getOrThrow()
        assertEquals("ready", result.state)
        assertEquals(listOf(":arch"), result.modules)
    }

    fun `test 読み直しが失敗すると前の答えを stale として返し 失敗の理由を付ける`() {
        installAll()
        loaded()
        gradle.loads += LoadAnswer(fails = true)
        val result = call { it.refreshDefinition(null) }.getOrThrow()
        assertEquals("ready", result.state)
        assertTrue(result.stale)
        assertTrue(result.staleReason!!.startsWith("loading the changed definition failed"))
        assertNotNull(result.loadFailure)
    }

    fun `test 一度も読めないまま失敗すると unavailable と LoadFailed を返す`() {
        installAll()
        gradle.loads += LoadAnswer(fails = true)
        val result = call { it.refreshDefinition(null) }.getOrThrow()
        assertEquals("unavailable", result.state)
        assertEquals("LoadFailed", result.unavailableReason)
        assertEquals(emptyList<Any>(), result.modules)
    }

    fun `test refresh が時間内に終わらなければ待つのをやめて断る`() {
        installAll()
        loaded()
        val gate = CompletableDeferred<Unit>()
        gradle.loads += LoadAnswer(gate = gate)
        val failure = call(tools(refreshTimeout = 300.milliseconds)) { it.refreshDefinition(null) }.exceptionOrNull()
        gate.complete(Unit)
        assertEquals("katachi: reloading the katachi definition did not finish within 300ms. Gradle may still be running in the IDE; wait for it, then call this tool again.", failure?.message)
    }

    fun `test 知らない module の refresh は Gradle を回さずに断る`() {
        installAll()
        loaded()
        val failure = call { it.refreshDefinition(":nope") }.exceptionOrNull()
        assertTrue("$failure", failure is KatachiMcpToolException.UnknownModule)
        assertEquals(1, gradle.loadRequests.size)
    }

    fun `test run_check は保存してから検査を走らせ 結果と先頭の違反を返す`() {
        installAll()
        loaded()
        val stray = writeFile("src/other/Stray.kt", "")
        gradle.checks += PlannedCheck(sarif = sarif("src/other/Stray.kt", "src/other/Stray2.kt"), fails = true)
        val result = call { it.runCheck(null) }.getOrThrow()
        assertEquals(listOf(":arch:test"), gradle.checkRequests.single().taskNames)
        assertEquals(":arch", result.module)
        assertEquals("failed", result.outcome)
        assertEquals(2, result.violationCount)
        assertEquals(stray.toNioPath().toString(), result.violations.first().path)
        assertFalse(result.truncated)
    }

    fun `test run_check は時間内に終わらなければ timedOut を返す`() {
        installAll()
        loaded()
        val gate = CompletableDeferred<Unit>()
        gradle.checks += PlannedCheck(sarif = sarif("src/other/Stray.kt"), gate = gate)
        val result = call(tools(checkTimeout = 300.milliseconds)) { it.runCheck(":arch") }.getOrThrow()
        gate.complete(Unit)
        assertEquals("timedOut", result.outcome)
    }

    fun `test 定義モジュールが複数あると module を省いた run_check は一覧を添えて断る`() {
        synced = syncedWithDefinition(":arch", ":arch2")
        gradle.definitionOf["arch2"] = definitionJson("synthetic-definition-explained")
        installAll()
        loaded()
        val failure = call { it.runCheck(null) }.exceptionOrNull()
        assertEquals(
            "katachi: this project has more than one katachi definition module, so the tool cannot pick one. Pass module as one of :arch, :arch2.",
            failure?.message,
        )
        assertEquals(emptyList<Any>(), gradle.checkRequests)
    }
}
