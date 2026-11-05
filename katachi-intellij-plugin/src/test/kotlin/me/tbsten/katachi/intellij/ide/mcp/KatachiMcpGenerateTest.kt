package me.tbsten.katachi.intellij.ide.mcp

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.generate.ArgumentProblem
import me.tbsten.katachi.intellij.data.generate.HeadlessGenerationResult
import me.tbsten.katachi.intellij.ide.KatachiIdeTestBase
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.sarif.FakeViolationsService
import me.tbsten.katachi.intellij.model.GeneratedFile
import me.tbsten.katachi.intellij.model.GenerationItemReport
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.WrittenKind
import me.tbsten.katachi.intellij.presentation.FieldError
import me.tbsten.katachi.intellij.presentation.GenerateBlocker
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.ResourceBundle

/** katachi_generate over a scripted generation: the setting, the refusals and how a report is told. */
internal class KatachiMcpGenerateTest : BasePlatformTestCase() {
    private val root = Path.of("/repo")
    private val arch = KatachiModule(":arch", root.resolve("arch"), root, "repo", emptySet())
    private val gradle = FakeGradleActions(modules = listOf(arch))

    private fun tools(hasModule: Boolean = true) =
        KatachiMcpTools(DefinitionPorts({ hasModule }, { hasModule }, { FakeDefinitionModelService() }, { FakeViolationsService() }), root, gradle)

    private fun generate(
        templates: List<String> = listOf("api.Controller"),
        arguments: Map<String, String> = mapOf("name" to "User"),
        onExisting: McpOnExisting = McpOnExisting.Fail,
        module: String = ":arch",
    ): Result<GenerateResult> = runCatching { runBlocking { tools().generate(module, templates, arguments, onExisting) } }

    fun `test 設定がオフなら生成を呼ばずに 設定の名前を添えて断る`() {
        gradle.allowsGenerate = false
        val failure = generate().exceptionOrNull()
        assertEquals(
            "katachi: generating files over MCP is turned off. Turn on 'Allow AI agents to generate files from templates over MCP' in Settings | Tools | katachi.",
            failure?.message,
        )
        assertEquals(emptyList<Any>(), gradle.generations)
    }

    fun `test 設定の名前は英語の文言と同じ`() {
        val english = ResourceBundle.getBundle("messages.KatachiBundle", Locale.ROOT).getString("settings.mcpAllowGenerate")
        assertEquals(english, KatachiMcpToolException.ALLOW_GENERATE_SETTING)
    }

    fun `test 引数は onExisting とともにそのまま本体に渡る`() {
        gradle.generateWith = HeadlessGenerationResult.Ran(GenerationReport(emptyList()), timedOut = false)
        generate(templates = listOf("a", "a", "b"), onExisting = McpOnExisting.Skip, module = "arch").getOrThrow()
        val request = gradle.generations.single()
        assertEquals(arch, request.module)
        assertEquals(listOf("a", "b"), request.templates)
        assertEquals(mapOf("name" to "User"), request.arguments)
        assertEquals(OnExistingChoice.Skip, request.onExisting)
    }

    fun `test 知らない module と空の templates は生成を呼ばずに断る`() {
        assertTrue(generate(module = ":nope").exceptionOrNull() is KatachiMcpToolException.UnknownModule)
        assertTrue(generate(templates = emptyList()).exceptionOrNull() is KatachiMcpToolException.InvalidArguments)
        assertEquals(emptyList<Any>(), gradle.generations)
    }

    fun `test 生成中なら busy 知らないテンプレートなら知っている id を並べて断る`() {
        gradle.generateWith = HeadlessGenerationResult.Busy
        assertTrue(generate().exceptionOrNull() is KatachiMcpToolException.Busy)
        gradle.generateWith = HeadlessGenerationResult.UnknownTemplates(listOf("api.Nope"), listOf("api.Controller", "domain.Service"))
        assertEquals(
            "katachi: api.Nope is not a template of this module. Pass ids as katachi_list_templates gives them: api.Controller, domain.Service.",
            generate().exceptionOrNull()?.message,
        )
    }

    fun `test 不正な引数は欄ごとの理由を並べて断る`() {
        val controller = TemplateId(arch.id, "api.Controller")
        gradle.generateWith = HeadlessGenerationResult.Invalid(
            listOf(
                ArgumentProblem.Field("api.Controller", GenerateBlocker.InvalidField(controller, "resource", FieldError.Required)),
                ArgumentProblem.Field("api.Controller", GenerateBlocker.InvalidField(controller, "kind", FieldError.NotAcceptedValue("x", listOf("a", "b")))),
                ArgumentProblem.UnknownArgument("nmae"),
            ),
        )
        assertEquals(
            "katachi: the arguments cannot generate the templates: api.Controller: resource is required; " +
                "api.Controller: kind must be one of a, b, not x; nmae is a parameter of none of the templates. " +
                "Fix them and call this tool again; katachi_list_templates lists the parameters. Nothing was written.",
            generate().exceptionOrNull()?.message,
        )
    }

    fun `test 結果は run ごとに 書いたファイル ぶつかったパス 走らなかったものを返す`() {
        val id = { name: String -> TemplateId(ModuleId(root, ":arch"), name) }
        val written = root.resolve("src/A.kt")
        val existing = root.resolve("src/B.kt")
        gradle.generateWith = HeadlessGenerationResult.Ran(
            GenerationReport(
                listOf(
                    GenerationItemReport(listOf(id("a")), GenerationItemResult.Generated(listOf(GeneratedFile(written, WrittenKind.New)))),
                    GenerationItemReport(listOf(id("set")), GenerationItemResult.StoppedAtConflict(listOf(existing))),
                    GenerationItemReport(listOf(id("c")), GenerationItemResult.NotRun),
                ),
            ),
            timedOut = false,
        )
        val result = generate().getOrThrow()
        assertEquals(listOf("generated", "stoppedAtConflict", "notRun"), result.items.map { it.result })
        assertEquals(listOf(GeneratedFileDto(written.toString(), "new")), result.items[0].files)
        assertEquals(listOf(existing.toString()), result.items[1].existingPaths)
        assertEquals(listOf(written.toString()), result.writtenFiles)
        assertFalse(result.timedOut)
    }
}

/** katachi_generate over the real project service, a fake Gradle that writes real files and the real VFS. */
internal class KatachiMcpGenerateInIdeTest : KatachiIdeTestBase() {
    private val dataDir get() = root.resolve("data/src/main/kotlin/com/example/data")

    fun `test 生成したファイルがディスクと VFS に載り エディタでは開かない`() {
        installService()
        val settings = KatachiSettings.getInstance(project)
        settings.mcpAllowGenerate = true
        try {
            val tools = KatachiMcpTools(DefinitionPorts({ true }, { true }, { FakeDefinitionModelService() }, { FakeViolationsService() }), root, ProjectGradleActions(project))
            val running = scope.async { runCatching { tools.generate(":arch-a", listOf("data.Repository"), mapOf("name" to "User"), McpOnExisting.Fail) } }
            PlatformTestUtil.waitWithEventsDispatching({ "generate did not return" }, { running.isCompleted }, 20)
            val result = runBlocking { running.await() }.getOrThrow()

            assertEquals(listOf("generated"), result.items.map { it.result })
            val first = dataDir.resolve("UserRepository.kt")
            assertTrue(result.writtenFiles.toString(), first.toString() in result.writtenFiles)
            assertTrue(Files.exists(first))
            assertNotNull(LocalFileSystem.getInstance().findFileByNioFile(first))
            assertEquals(emptyList<Path>(), openFiles())
            assertEquals("fail", gradle.generationRequests.single().tasks.single().args.toMap()["onExisting"])
        } finally {
            settings.mcpAllowGenerate = false
        }
    }
}
