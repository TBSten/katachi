package me.tbsten.katachi.intellij.ide.mcp

import com.intellij.mcpserver.ClientInfo
import com.intellij.mcpserver.McpCallAdditionalDataElement
import com.intellij.mcpserver.McpCallInfo
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCallResultContent
import com.intellij.mcpserver.McpToolFilter
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.asTools
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.definition.DefinitionServiceTestBase
import me.tbsten.katachi.intellij.ide.definition.KatachiDefinitionService
import me.tbsten.katachi.intellij.ide.sarif.KatachiViolationsService

/** The shell as the MCP Server calls it: the JSON arguments of a `tools/call` in, the result or the error text out. */
internal class KatachiMcpToolsetTest : DefinitionServiceTestBase() {
    override fun setUp() {
        super.setUp()
        install()
        project.replaceService(KatachiDefinitionService::class.java, definition, testRootDisposable)
        // The light project outlives the test: a service the shell creates on its own would keep this test's project service.
        project.replaceService(KatachiViolationsService::class.java, KatachiViolationsService(project, scope), testRootDisposable)
        loaded()
    }

    /** Off the EDT while the EDT keeps dispatching: run_check and refresh_definition hop to it (saving, reloading). */
    private fun callPumping(name: String, arguments: JsonObject): Result<McpToolCallResult> {
        val running = scope.async { callTool(name, arguments) }
        PlatformTestUtil.waitWithEventsDispatching({ "$name did not return" }, { running.isCompleted }, 20)
        return runBlocking { running.await() }
    }

    private fun callTool(name: String, arguments: JsonObject): Result<McpToolCallResult> {
        val tool = KatachiMcpToolset().asTools().single { it.descriptor.name == name }
        val info = McpCallInfo(
            callId = 1,
            clientInfo = ClientInfo("katachi-test", "0"),
            project = project,
            mcpToolDescriptor = tool.descriptor,
            rawArguments = arguments,
            meta = JsonObject(emptyMap()),
            mcpSessionOptions = McpServerService.McpSessionOptions(McpServerService.AskCommandExecutionMode.DONT_ASK, McpToolFilter.AllowAll),
            headers = emptyMap(),
        )
        return runCatching { runBlocking { withContext(McpCallAdditionalDataElement(info)) { tool.call(arguments) } } }
    }

    private val McpToolCallResult.text: String get() = content.joinToString("\n") { (it as? McpToolCallResultContent.Text)?.text ?: it.toString() }

    fun `test 引数の JSON が本体に渡り 結果が JSON で返る`() {
        writeFile("src/service/HealthService.kt", "class HealthService\n")
        val result = callTool("katachi_roles_of_file", buildJsonObject { put("path", "src/service/HealthService.kt") }).getOrThrow()
        assertFalse(result.text, result.isError)
        val structured = result.structuredContent ?: throw AssertionError("no structured content: ${result.text}")
        assertEquals("\"matched\"", structured["outcome"].toString())
        assertTrue(structured["roles"].toString(), structured["roles"].toString().contains("\"qualifiedName\":\"Service\""))
    }

    fun `test 省略できる引数は省いて呼べる`() {
        val result = callTool("katachi_list_roles", buildJsonObject { }).getOrThrow()
        assertFalse(result.text, result.isError)
        assertTrue(result.text, result.text.contains("\":arch\""))
    }

    fun `test 本体が断ると mcpFail の文面がそのままエラーとして返る`() {
        val result = callTool("katachi_list_templates", buildJsonObject { put("module", ":nope") })
        val text = result.fold({ if (it.isError) it.text else throw AssertionError("not an error: ${it.text}") }, { it.message.orEmpty() })
        assertEquals("katachi: :nope is not a katachi definition module. Pass one of :arch, or leave module out to read every one.", text)
    }

    fun `test refresh_definition は JSON で呼べ 読み込み後の状態を返す`() {
        val result = callPumping("katachi_refresh_definition", buildJsonObject { put("module", ":arch") }).getOrThrow()
        assertFalse(result.text, result.isError)
        assertEquals("\"ready\"", result.structuredContent?.get("state").toString())
        assertEquals(2, gradle.loadRequests.size)
    }

    fun `test run_check に知らない module を渡すと Gradle を回さず mcpFail の文面で返る`() {
        val result = callPumping("katachi_run_check", buildJsonObject { put("module", ":nope") })
        val text = result.fold({ if (it.isError) it.text else throw AssertionError("not an error: ${it.text}") }, { it.message.orEmpty() })
        assertEquals("katachi: :nope is not a katachi definition module. Pass one of :arch, or leave module out to read every one.", text)
        assertEquals(emptyList<Any>(), gradle.checkRequests)
    }

    fun `test generate は設定がオフなら mcpFail の文面で断る`() {
        val result = callTool("katachi_generate", buildJsonObject { put("module", ":arch"); put("templates", JsonArray(listOf(JsonPrimitive("a")))) })
        val text = result.fold({ if (it.isError) it.text else throw AssertionError("not an error: ${it.text}") }, { it.message.orEmpty() })
        assertTrue(text, text.startsWith("katachi: generating files over MCP is turned off."))
        assertEquals(emptyList<Any>(), gradle.generationRequests)
    }

    fun `test generate の onExisting に overwrite は渡せない`() {
        KatachiSettings.getInstance(project).mcpAllowGenerate = true
        try {
            val arguments = buildJsonObject {
                put("module", ":arch")
                put("templates", JsonArray(listOf(JsonPrimitive("a"))))
                put("onExisting", "overwrite")
            }
            val result = callTool("katachi_generate", arguments)
            assertTrue("$result", result.fold({ it.isError }, { true }))
            // Refused while reading the arguments: the body would have read the definition again first.
            assertEquals(1, gradle.loadRequests.size)
            assertEquals(emptyList<Any>(), gradle.generationRequests)
        } finally {
            KatachiSettings.getInstance(project).mcpAllowGenerate = false
        }
    }

    fun `test 必須の引数が無ければ本体を呼ばずにエラーになる`() {
        val result = callTool("katachi_roles_of_file", buildJsonObject { })
        val isError = result.fold({ it.isError }, { true })
        assertTrue("$result", isError)
        assertEquals(1, gradle.loadRequests.size)
    }

    fun `test katachi-mcp xml で登録した tool が MCP Server の一覧に出る`() {
        val names = McpToolsProvider.EP.extensionList.flatMap { it.getTools() }.map { it.descriptor.name }
        assertEquals(KATACHI_TOOL_NAMES, names.filter { it.startsWith("katachi_") }.sorted())
    }

    companion object {
        val KATACHI_TOOL_NAMES: List<String> = listOf(
            "katachi_generate",
            "katachi_list_roles",
            "katachi_list_templates",
            "katachi_refresh_definition",
            "katachi_roles_of_file",
            "katachi_run_check",
            "katachi_violations",
        )
    }
}
