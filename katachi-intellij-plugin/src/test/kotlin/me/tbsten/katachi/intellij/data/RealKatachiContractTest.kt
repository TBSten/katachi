package me.tbsten.katachi.intellij.data

import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.generate.GenerationItem
import me.tbsten.katachi.intellij.data.generate.GenerationListener
import me.tbsten.katachi.intellij.data.generate.GenerationSession
import me.tbsten.katachi.intellij.data.generate.TemplateRunStatus
import me.tbsten.katachi.intellij.data.generate.parseTemplateOutput
import me.tbsten.katachi.intellij.data.generate.templateArgsOf
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.load.LoadResult
import me.tbsten.katachi.intellij.data.load.TemplateDescriptionLoader
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.GeneratedFile
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.WrittenKind
import me.tbsten.katachi.intellij.presentation.ExpectedLocation
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.presentation.expectedFilesOf
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Pins the plugin to what katachi really prints and writes. The fixtures `json/sample-*.json` and
 * `output/real-jvm-*.log` are unedited outputs of the samples (only the project root is replaced by
 * `file:///__ROOT__`), so a change of shape on katachi's side makes this test fail once the
 * fixtures are refreshed from the samples.
 */
class RealKatachiContractTest {
    private val root: Path = Paths.get("/work/sample jvm")
    private val definition = KatachiModule(":architecture-test", root.resolve("architecture-test"), root, "katachi-sample-jvm")

    /** The service file the real `katachiTemplate --arg name=IdePluginProbe` runs wrote. */
    private val probeService = root.resolve("src/main/kotlin/com/example/service/IdePluginProbeService.kt")

    private fun detailOf(fixture: String, roleName: String): TemplateDetailModel =
        ContractFixtures.templates(fixture).single { it.roleName == roleName }.detail ?: throw AssertionError("$roleName has no detail")

    private fun realOutput(name: String): List<String> = ContractFixtures.outputLines("real-jvm-$name", root)

    private fun outcomeOf(name: String): GradleRunOutcome =
        if (ContractFixtures.exitCode("real-jvm-$name") == 0) GradleRunOutcome.Succeeded else GradleRunOutcome.Failed

    // ---- JSON of the three samples ----

    @Test
    fun `sample-jvmのJSONから予想したパスが実際に書かれたパスと一致する`() {
        val service = detailOf("sample-jvm", "domain/Service")
        val expected = expectedFilesOf(service, mapOf("name" to "IdePluginProbe")).single()
        val location = expected.location as? ExpectedLocation.Known ?: throw AssertionError("${expected.location}")

        assertEquals(parseTemplateOutput(realOutput("success")).written, listOf(root.resolve(location.path)))
    }

    @Test
    fun `sample-jvmのJSONから組んだ引数は実際に通した引数と同じになる`() {
        val service = detailOf("sample-jvm", "domain/Service")
        val args = templateArgsOf("domain/Service", service, mapOf("name" to "IdePluginProbe", "kdoc" to ""), OnExistingChoice.Fail)
        assertEquals(listOf("roleName" to "domain/Service", "onExisting" to "fail", "name" to "IdePluginProbe"), args)
    }

    @Test
    fun `sample-androidのJSONは他のパラメータを読む既定値をそのまま持ち空なら送らない`() {
        val component = detailOf("sample-android", "ui/Component")
        assertEquals(listOf("name", "previewText"), component.parameters.map { it.name })
        assertEquals("\${name}", component.parameters[1].default)
        assertTrue(component.parameters[0].isRequired)
        val files = expectedFilesOf(component, mapOf("name" to "Button"))
        assertEquals(ExpectedLocation.Known("ui/src/main/kotlin/com/example/sample/ui/component/AppButton.kt"), files.single().location)
        val args = templateArgsOf("ui/Component", component, mapOf("name" to "Button"), OnExistingChoice.Fail)
        assertEquals(listOf("roleName", "onExisting", "name"), args.map { it.first })
    }

    @Test
    fun `sample-kmpのJSONは2ファイルとBooleanの分岐で1ファイルに減る`() {
        val template = ContractFixtures.templates("sample-kmp").single()
        val repository = template.detail ?: throw AssertionError("no detail")
        assertEquals(2, template.summary.fileCount)
        val withImpl = repository.parameters.single { it.name == "withImpl" }
        assertTrue(withImpl is ParameterModel.BooleanParam)
        assertEquals("true", withImpl.default)

        val both = expectedFilesOf(repository, mapOf("name" to "User"))
        assertEquals(listOf("UserRepository.kt", "UserRepositoryImpl.kt"), both.map { it.fileName })
        assertEquals(
            ExpectedLocation.Known("data/src/commonMain/kotlin/com/example/kmp/data/user/UserRepositoryImpl.kt"),
            both[1].location,
        )
        val interfaceOnly = expectedFilesOf(repository, mapOf("name" to "User", "withImpl" to "false"))
        assertEquals(listOf("UserRepository.kt"), interfaceOnly.map { it.fileName })
    }

    @Test
    fun `sample-kmpで既定値のままのBooleanは送らず変えたら送る`() {
        val repository = detailOf("sample-kmp", "data/Repository")
        val untouched = templateArgsOf("data/Repository", repository, mapOf("name" to "User"), OnExistingChoice.Fail)
        assertEquals(listOf("roleName", "onExisting", "name"), untouched.map { it.first })
        val changed = templateArgsOf("data/Repository", repository, mapOf("name" to "User", "withImpl" to "false"), OnExistingChoice.Fail)
        assertEquals("withImpl" to "false", changed.last())
    }

    // ---- katachiInternalTemplatesJson ----

    @Test
    fun `JSONのタスクがキャッシュから戻り何も出力しなくてもファイルから読む`() = runBlocking {
        val fs = FakeFileSystem()
        val lines = realOutput("internalTemplatesJson")
        assertTrue(lines.any { it.endsWith(":katachiInternalTemplatesJson FROM-CACHE") })
        val runner = FakeGradleTaskRunner(fs) { _, _ ->
            FakeRun(lines, writes = mapOf(definition.templateDescriptionJson to ContractFixtures.json("sample-jvm")))
        }
        val result = TemplateDescriptionLoader(runner, fs).load(listOf(definition), emptyMap(), object : GradleRunListener {})

        val loaded = result as? LoadResult.Loaded ?: throw AssertionError("$result")
        assertEquals(listOf("domain/Service"), loaded.snapshots.single().templates.map { it.roleName })
        assertEquals(listOf(":architecture-test:katachiInternalTemplatesJson"), runner.requests.single().taskNames)
    }

    // ---- katachiTemplate outputs ----

    @Test
    fun `実際の成功出力から書いたファイルとルートを読む`() {
        val output = parseTemplateOutput(realOutput("success"))
        assertEquals(root, output.projectRoot)
        assertEquals(listOf(probeService), output.written)
        assertTrue(output.reachedKatachi)
        assertEquals(TemplateRunStatus.Ok, output.status)
    }

    @Test
    fun `実際のskip出力から書かなかった既存ファイルを読む`() {
        val output = parseTemplateOutput(realOutput("skip"))
        assertEquals(listOf(probeService), output.skippedExisting)
        assertEquals(emptyList<Path>(), output.written)
        assertEquals(TemplateRunStatus.Ok, output.status)
    }

    @Test
    fun `実際のoverwrite出力から上書きしたファイルを読む`() {
        val output = parseTemplateOutput(realOutput("overwrite"))
        assertEquals(listOf(probeService), output.overwritten)
        assertEquals(listOf(probeService), output.written)
    }

    @Test
    fun `実際の衝突出力から既存ファイルを読む`() {
        val status = parseTemplateOutput(realOutput("conflict-fail")).status as? TemplateRunStatus.Failed ?: throw AssertionError()
        assertEquals(listOf(probeService), status.conflicting)
    }

    @Test
    fun `実際の必須引数の欠落はkatachiの失敗本文として読み衝突にしない`() {
        val status = parseTemplateOutput(realOutput("missing-required-arg")).status as? TemplateRunStatus.Failed ?: throw AssertionError()
        assertNull(status.conflicting)
        assertTrue(status.body.first(), status.body.first().startsWith("Template of role \"domain/Service\""))
        assertTrue(status.body.any { "--arg name=<value>" in it })
    }

    @Test
    fun `実際の知らないroleはkatachiのまとめまで届かずプロセッサの拒否に分類する`() = runBlocking {
        val output = parseTemplateOutput(realOutput("unknown-role"))
        assertFalse(output.reachedKatachi)
        assertEquals(TemplateRunStatus.Missing, output.status)

        val result = runOne("unknown-role")
        val failure = ((result as? GenerationItemResult.Failed)?.failure as? GenerationFailure.NotReached)?.failure
        val rejected = failure as? GradleFailure.ProcessorRejected ?: throw AssertionError("$result")
        assertEquals("me.tbsten.katachi.template.KatachiUnknownTemplateRoleException", rejected.exceptionClassName)
        assertTrue(rejected.details.first().startsWith("Unknown role \"domain/NoSuchRole\""))
    }

    // ---- the whole generation over real outputs ----

    @Test
    fun `実際の出力で生成セッションが新規と上書きとスキップを見分ける`() = runBlocking {
        assertEquals(GenerationItemResult.Generated(listOf(GeneratedFile(probeService, WrittenKind.New))), runOne("success"))
        assertEquals(GenerationItemResult.Generated(listOf(GeneratedFile(probeService, WrittenKind.Overwritten))), runOne("overwrite"))
        assertEquals(GenerationItemResult.Skipped(listOf(probeService)), runOne("skip"))
    }

    @Test
    fun `実際の衝突出力で生成セッションが衝突を尋ね上書きなら同じ1件をoverwriteで再実行する`() = runBlocking {
        val asked = mutableListOf<ConflictQuestion>()
        val runner = FakeGradleTaskRunner { _, index ->
            val name = if (index == 0) "conflict-fail" else "overwrite"
            FakeRun(realOutput(name), outcomeOf(name))
        }
        val report = GenerationSession(runner, FakeFileSystem()) { question ->
            asked += question
            ConflictChoice.Overwrite
        }.run(listOf(probeItem()), OnExistingChoice.Fail, object : GenerationListener {})

        assertEquals(listOf(probeService), asked.single().existing)
        assertEquals(listOf("fail", "overwrite"), runner.requests.map { request -> request.tasks.single().args.first { it.first == "onExisting" }.second })
        assertEquals(GenerationItemResult.Generated(listOf(GeneratedFile(probeService, WrittenKind.Overwritten))), report.items.single().result)
    }

    @Test
    fun `実際の必須引数の欠落で生成セッションはkatachiの失敗として止まる`() = runBlocking {
        val result = runOne("missing-required-arg") as? GenerationItemResult.Failed ?: throw AssertionError()
        val body = (result.failure as? GenerationFailure.Katachi)?.body ?: throw AssertionError("${result.failure}")
        assertTrue(body.first().contains("was run without values for: name."))
    }

    private fun probeItem() = GenerationItem(
        templateId = TemplateId(definition.id, "domain/Service"),
        module = definition,
        args = listOf("roleName" to "domain/Service", "name" to "IdePluginProbe"),
        expectedPaths = listOf(probeService),
    )

    private suspend fun runOne(name: String): GenerationItemResult {
        val runner = FakeGradleTaskRunner { _, _ -> FakeRun(realOutput(name), outcomeOf(name)) }
        val report = GenerationSession(runner, FakeFileSystem()) { ConflictChoice.Stop }
            .run(listOf(probeItem()), OnExistingChoice.Fail, object : GenerationListener {})
        assertEquals(":architecture-test:katachiTemplate", runner.requests.single().tasks.single().taskPath)
        return report.items.single().result
    }
}
