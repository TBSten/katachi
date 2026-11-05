package me.tbsten.katachi.intellij.ide.mcp

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.ide.DefinitionReload
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.sarif.FakeViolationsService
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import me.tbsten.katachi.intellij.testing.DefinitionStateScripts
import me.tbsten.katachi.intellij.testing.ViolationsStateScripts
import java.nio.file.Path
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/** The tools over a scripted definition and violations service: what each definition state and violations state gives. */
internal class KatachiMcpToolsStateTest : BasePlatformTestCase() {
    private val projectRoot = Path.of("/repo")
    private val arch = KatachiModule(":arch", projectRoot.resolve("arch"), projectRoot, "repo", emptySet())
    private val other = KatachiModule(":other", projectRoot.resolve("other"), projectRoot, "repo", emptySet())
    private val snapshot = DefinitionSnapshot(arch, DefinitionFixtures.definition("synthetic-definition-explained"), emptyList(), "0.4.0", DEFINITION_INSTANT)
    private val ready = DefinitionState.Ready(listOf(snapshot), Freshness.Fresh)

    private val definitions = FakeDefinitionModelService()
    private val violations = FakeViolationsService()
    private val gradle = FakeGradleActions(modules = listOf(arch))

    private fun tools(hasModule: Boolean = true) = KatachiMcpTools(
        DefinitionPorts({ hasModule }, { hasModule }, { definitions }, { violations }),
        projectBase = projectRoot,
        gradle = gradle,
        loadTimeout = 100.milliseconds,
    )

    private fun failureOf(block: suspend () -> Any): KatachiMcpToolException {
        val failure = runCatching { runBlocking { block() } }.exceptionOrNull()
        return failure as? KatachiMcpToolException ?: throw AssertionError("expected a KatachiMcpToolException, got $failure", failure)
    }

    fun `test 定義モジュールが無いプロジェクトでは全 tool が同じ文面で断る`() {
        val texts = listOf(
            failureOf { tools(hasModule = false).rolesOfFile("src/A.kt") },
            failureOf { tools(hasModule = false).listRoles(null) },
            failureOf { tools(hasModule = false).listTemplates(null) },
            failureOf { tools(hasModule = false).violations(null, null) },
        ).map { it.message }
        assertEquals(1, texts.distinct().size)
        assertTrue(texts.first()!!.startsWith("katachi: this project has no katachi definition module. "))
    }

    fun `test 未読み込みなら ensureLoaded を1回呼び 読み終わらないと待ち時間を添えて断る`() {
        val failure = failureOf { tools().listRoles(null) }
        assertEquals(1, definitions.ensureLoadedCalls)
        assertTrue(failure is KatachiMcpToolException.LoadTimedOut)
    }

    fun `test 読み込み中でも前の答えがあればそれを stale として返す`() {
        definitions.emit(DefinitionState.Loading(ready))
        val result = runBlocking { tools().listRoles(null) }
        assertEquals(0, definitions.ensureLoadedCalls)
        assertTrue(result.stale)
        assertTrue(result.staleReason!!.startsWith("loading the definition did not finish"))
        assertEquals(listOf(":arch"), result.modules.map { it.module })
    }

    fun `test 自動読み込みがオフで読めないときはその理由をそのまま返し Gradle を回さない`() {
        definitions.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.LoadingWithoutUserDisabled, null))
        val failure = failureOf { tools().rolesOfFile("src/service/HealthService.kt") }
        assertEquals(DefinitionUnavailableReason.LoadingWithoutUserDisabled, (failure as? KatachiMcpToolException.DefinitionUnavailable)?.reason)
        assertTrue(failure.message!!.contains("Settings | Tools | katachi"))
        assertEquals(0, definitions.ensureLoadedCalls)
        assertEquals(emptyList<Any>(), definitions.refreshes)
    }

    fun `test Stale の答えはエラーにせず stale と理由を付けて返す`() {
        definitions.emit(ready.copy(freshness = Freshness.Stale(DEFINITION_INSTANT, StaleReason.DefinitionChanged)))
        val result = runBlocking { tools().listRoles(":arch") }
        assertTrue(result.stale)
        assertEquals("the definition changed at $DEFINITION_INSTANT and has not been loaded again", result.staleReason)
    }

    fun `test module は コロン有り無しどちらでも引け 知らない module は知っているものを並べて断る`() {
        definitions.emit(ready)
        assertEquals(listOf(":arch"), runBlocking { tools().listRoles("arch") }.modules.map { it.module })
        val failure = failureOf { tools().listTemplates(":nope") }
        assertEquals("katachi: :nope is not a katachi definition module. Pass one of :arch, or leave module out to read every one.", failure.message)
    }

    fun `test 状態のどの列でも tool は答えるか KatachiMcpToolException で断るかのどちらかで stale は状態と食い違わない`() {
        val random = Random(220)
        repeat(10) {
            for (state in DefinitionStateScripts.random(random, 12, listOf(snapshot))) {
                definitions.emit(state)
                val result = runCatching { runBlocking { tools().listRoles(null) } }
                val failure = result.exceptionOrNull()
                if (failure != null) {
                    assertTrue("$state -> $failure", failure is KatachiMcpToolException)
                    assertNull("$state has an answer but the tool refused: $failure", state.answer)
                } else {
                    val fresh = state is DefinitionState.Ready && state.freshness == Freshness.Fresh
                    assertEquals("$state", !fresh, result.getOrThrow().stale)
                }
            }
        }
    }

    fun `test violations は NotRun なら空で 読めていれば module と path で絞れる`() {
        val inArch = ViolationsStateScripts.violation(arch.id, projectRoot.resolve("src/A.kt"))
        val inOther = ViolationsStateScripts.violation(other.id, projectRoot.resolve("src/B.kt"), "katachi.MissingFile")
        assertEquals(ViolationsResult("notRun", null, false, null, emptyList()), tools().violations(null, null))

        violations.emit(ViolationsState.Ready(listOf(inArch, inOther), DEFINITION_INSTANT, stale = true, paths = emptyList()))
        val all = tools().violations(null, null)
        assertEquals("ready", all.state)
        assertEquals(DEFINITION_INSTANT.toString(), all.readAt)
        assertTrue(all.stale)
        assertEquals(listOf("katachi.UnexpectedFile", "katachi.MissingFile"), all.violations.map { it.ruleId })
        assertEquals(listOf(":other"), tools().violations(null, "other").violations.map { it.module })
        assertEquals(listOf("src/B.kt"), tools().violations("src/B.kt", null).violations.map { Path.of(it.path!!).let(projectRoot::relativize).toString() })
        assertEquals(emptyList<Any>(), tools().violations("src/B.kt", ":arch").violations)
        assertTrue(runCatching { tools().violations(null, ":nope") }.exceptionOrNull() is KatachiMcpToolException.UnknownModule)
    }

    fun `test violations は SARIF が読めないとき前の答えと理由を返す`() {
        val previous = ViolationsState.Ready(listOf(ViolationsStateScripts.violation(arch.id, projectRoot.resolve("src/A.kt"))), DEFINITION_INSTANT, false, emptyList())
        violations.emit(ViolationsState.Unreadable(Path.of("/repo/arch/build/reports/katachi/katachi.sarif"), "truncated", previous))
        val result = tools().violations(null, null)
        assertEquals("unreadable", result.state)
        assertEquals("/repo/arch/build/reports/katachi/katachi.sarif: truncated", result.unreadableReason)
        assertEquals(1, result.violations.size)
    }

    fun `test ツールウィンドウが生成中なら refresh は待たずに busy で断る`() {
        gradle.reloadWith = DefinitionReload.Busy
        val failure = failureOf { tools().refreshDefinition(null) }
        assertTrue("$failure", failure is KatachiMcpToolException.Busy)
        assertEquals(listOf<KatachiModule?>(null), gradle.reloads)
    }

    fun `test refresh に module を渡すとその module だけを読み直す`() {
        gradle.reloadWith = DefinitionReload.Done(ready, DefinitionErrors.None, null)
        val result = runBlocking { tools().refreshDefinition("arch") }
        assertEquals(listOf<KatachiModule?>(arch), gradle.reloads)
        assertEquals(listOf(":arch"), result.modules)
    }

    fun `test 定義モジュールが無いプロジェクトでは Gradle の tool も同じ文面で断り Gradle を回さない`() {
        val refresh = failureOf { tools(hasModule = false).refreshDefinition(null) }
        val check = failureOf { tools(hasModule = false).runCheck(null) }
        assertTrue(refresh is KatachiMcpToolException.NoDefinitionModule)
        assertTrue(check is KatachiMcpToolException.NoDefinitionModule)
        assertEquals(emptyList<Any>(), gradle.reloads)
        assertEquals(0, gradle.saves)
    }

    fun `test run_check は違反が多いと先頭の件数だけ返して truncated を付ける`() {
        val many = (1..25).map { ViolationsStateScripts.violation(arch.id, projectRoot.resolve("src/F$it.kt")) }
        violations.emit(ViolationsState.Ready(many, DEFINITION_INSTANT, false, emptyList()))
        val running = runBlocking {
            val call = async { tools().runCheck(null) }
            while (violations.runCheckCalls.isEmpty()) yield()
            violations.finishCheck(arch.id, GradleRunOutcome.Failed)
            call.await()
        }
        assertEquals("failed", running.outcome)
        assertEquals(25, running.violationCount)
        assertEquals(KatachiMcpTools.CHECK_VIOLATIONS_SHOWN, running.violations.size)
        assertTrue(running.truncated)
    }
}
