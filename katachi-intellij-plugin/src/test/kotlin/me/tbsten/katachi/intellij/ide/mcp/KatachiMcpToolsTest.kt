package me.tbsten.katachi.intellij.ide.mcp

import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.definition.DefinitionServiceTestBase
import me.tbsten.katachi.intellij.ide.sarif.FakeViolationsService
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason

/**
 * The tools over the real definition service, a fake Gradle that writes `definition.json` and the real VFS: what an AI agent
 * gets for a project whose `:arch` declares the synthetic definition.
 */
internal class KatachiMcpToolsTest : DefinitionServiceTestBase() {
    private fun tools(): KatachiMcpTools = KatachiMcpTools(DefinitionPorts({ true }, { true }, { definition }, { FakeViolationsService() }), projectBase = root)

    /** Runs [block] off the EDT while the EDT keeps dispatching: loading hops to the EDT, which a blocked EDT would never run. */
    private fun <T> call(block: suspend (KatachiMcpTools) -> T): Result<T> {
        val tools = tools()
        val running = scope.async { runCatching { block(tools) } }
        PlatformTestUtil.waitWithEventsDispatching({ "the tool call did not return" }, { running.isCompleted }, 20)
        return runBlocking { running.await() }
    }

    fun `test 未読み込みから呼ぶと定義を読んでからファイルの役割を答える`() {
        install()
        assertEquals(DefinitionState.NotLoaded, definition.state.value)
        writeFile("src/service/HealthService.kt", "class HealthService\n")

        val result = call { it.rolesOfFile("src/service/HealthService.kt") }.getOrThrow()

        assertEquals(listOf(":arch:katachiInternalDefinitionJson"), gradle.loadRequests.single().taskNames)
        assertEquals(":arch", result.module)
        assertEquals("src/service/HealthService.kt", result.relativePath)
        assertEquals("matched", result.outcome)
        assertEquals(listOf("Service"), result.roles.map { it.qualifiedName })
        assertFalse(result.stale)
    }

    fun `test 絶対パスでも引け どの役割も持たないファイルは理由と近い置き場所を返す`() {
        install()
        loaded()
        val result = call { it.rolesOfFile(root.resolve("src/other/Stray.kt").toString()) }.getOrThrow()
        assertEquals("unexpectedUnder", result.outcome)
        assertEquals("no role declares the directory src/other", result.detail)
        assertEquals(emptyList<Any>(), result.roles)
        assertEquals(listOf("src/thrower", "src/service"), result.nearby.map { it.directory })
    }

    fun `test 定義のどこにも入らないパスは PathNotCovered で断る`() {
        install()
        loaded()
        val failure = call { it.rolesOfFile("/elsewhere/Stray.kt") }.exceptionOrNull()
        assertEquals(
            "katachi: no katachi definition covers /elsewhere/Stray.kt. Pass a path inside the project a definition module checks, relative to the project root or absolute.",
            failure?.message,
        )
    }

    fun `test list_roles は定義モジュールごとに役割と layout を返す`() {
        install()
        val result = call { it.listRoles(null) }.getOrThrow()
        val module = result.modules.single()
        assertEquals(":arch", module.module)
        val service = module.roles.single { it.qualifiedName == "Service" }
        assertEquals(service.layout.map { it.pattern }, definition.definitions.single().definition!!.roles.single { it.qualifiedName == "Service" }.layout.map { it.pattern })
        assertTrue(service.declaredAt!!.matches(Regex("""[\w.]+\.kt:\d+""")))
    }

    fun `test list_templates は templateSet を含むテンプレートを引数と出力先つきで返す`() {
        gradle.definitionOf["arch"] = definitionJson("synthetic-definition-template-set")
        install()
        val templates = call { it.listTemplates(":arch") }.getOrThrow().modules.single().templates
        val expected = definition.definitions.single().templates
        assertEquals(expected.map { it.template }.toSet(), templates.map { it.id }.toSet())
        val set = templates.firstOrNull { it.setMembers != null } ?: throw AssertionError("no templateSet in $templates")
        assertTrue(set.setMembers!!.isNotEmpty())
        assertTrue(set.files.isNotEmpty())
        val plain = templates.first { it.setMembers == null && it.available }
        assertTrue("$plain", plain.parameters.isNotEmpty())
        assertNotNull(plain.exampleCommand)
    }

    fun `test katachi 0_2 のプロジェクトでは KatachiTooOld の理由で断る`() {
        synced = syncedWith(":arch-a")
        install()
        val failure = call { it.listRoles(null) }.exceptionOrNull()
        assertEquals(DefinitionUnavailableReason.KatachiTooOld, (failure as? KatachiMcpToolException.DefinitionUnavailable)?.reason)
    }
}
