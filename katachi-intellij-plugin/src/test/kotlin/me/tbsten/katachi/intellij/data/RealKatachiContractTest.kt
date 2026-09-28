package me.tbsten.katachi.intellij.data

import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.generate.GenerationItem
import me.tbsten.katachi.intellij.data.generate.GenerationListener
import me.tbsten.katachi.intellij.data.generate.GenerationSession
import me.tbsten.katachi.intellij.data.generate.TemplateArgsEntry
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

    /** [detailOf], but by the complete specifier: needed once a role has more than one template. */
    private fun detailByTemplate(fixture: String, template: String): TemplateDetailModel =
        ContractFixtures.templates(fixture).single { it.template == template }.detail ?: throw AssertionError("$template has no detail")

    private fun realOutput(name: String): List<String> = ContractFixtures.outputLines("real-jvm-$name", root)

    private fun outcomeOf(name: String): GradleRunOutcome =
        if (ContractFixtures.exitCode("real-jvm-$name") == 0) GradleRunOutcome.Succeeded else GradleRunOutcome.Failed

    // ---- JSON of the three samples ----

    @Test
    fun `sample-jvmのJSONから予想したパスが実際に書かれたパスと一致する`() {
        val service = detailOf("sample-jvm", "domain.Service")
        val expected = expectedFilesOf(service, mapOf("name" to "IdePluginProbe")).single()
        val location = expected.location as? ExpectedLocation.Known ?: throw AssertionError("${expected.location}")

        assertEquals(parseTemplateOutput(realOutput("success")).written, listOf(root.resolve(location.path)))
    }

    @Test
    fun `sample-jvmのJSONから組んだ引数は実際に通した引数と同じになる`() {
        val service = detailOf("sample-jvm", "domain.Service")
        val args = templateArgsOf("domain.Service", service, mapOf("name" to "IdePluginProbe", "kdoc" to ""), OnExistingChoice.Fail)
        assertEquals(listOf("template" to "domain.Service", "onExisting" to "fail", "name" to "IdePluginProbe"), args)
    }

    @Test
    fun `sample-androidのJSONは他のパラメータを読む既定値をそのまま持ち空なら送らない`() {
        val component = detailOf("sample-android", "ui.Component")
        // "name" is now a capture (the file name is `${name}.kt` directly, no more "App" prefix
        // katachi added inside the template), so it is no longer among `parameters`.
        assertEquals(listOf("previewText"), component.parameters.map { it.name })
        assertEquals("\${name}", component.parameters[0].default)
        assertEquals(listOf("name"), component.captures.map { it.name })
        assertTrue(component.captures.single().isRequired)
        val files = expectedFilesOf(component, mapOf("name" to "Button"))
        assertEquals(ExpectedLocation.Known("ui/src/main/kotlin/com/example/sample/ui/component/Button.kt"), files.single().location)
        val args = templateArgsOf("ui.Component", component, mapOf("name" to "Button"), OnExistingChoice.Fail)
        assertEquals(listOf("template", "onExisting", "name"), args.map { it.first })
    }

    @Test
    fun `sample-kmpのJSONはrepositoryとrepositoryImplの2つのテンプレートに分かれそれぞれ1ファイルを生成する`() {
        // The old single "data/Repository" role with a `withImpl` branch (2 files collapsing to 1)
        // is gone: the pair is now two templates of one role, each writing its own one file, chosen
        // together by `--arg template=data.Repository.repository,data.Repository.repositoryImpl`
        // (design draft section 6, "IDE の複数選択").
        val repository = detailByTemplate("sample-kmp-with-captures", "data.Repository.repository")
        val repositoryImpl = detailByTemplate("sample-kmp-with-captures", "data.Repository.repositoryImpl")
        assertEquals(listOf("item"), repository.parameters.map { it.name })
        assertEquals(listOf("name"), repository.captures.map { it.name })

        assertEquals(
            ExpectedLocation.Known("data/src/commonMain/kotlin/com/example/kmp/data/user/UserRepository.kt"),
            expectedFilesOf(repository, mapOf("name" to "User")).single().location,
        )
        assertEquals(
            ExpectedLocation.Known("data/src/commonMain/kotlin/com/example/kmp/data/user/UserRepositoryImpl.kt"),
            expectedFilesOf(repositoryImpl, mapOf("name" to "User")).single().location,
        )

        val entries = listOf(
            TemplateArgsEntry("data.Repository.repository", repository, mapOf("name" to "User")),
            TemplateArgsEntry("data.Repository.repositoryImpl", repositoryImpl, mapOf("name" to "User")),
        )
        assertEquals("template" to "data.Repository.repository,data.Repository.repositoryImpl", templateArgsOf(entries, OnExistingChoice.Fail).first())
    }

    @Test
    fun `sample-kmpで既定値のままのパラメータは送らず変えたら送る`() {
        val repository = detailOf("sample-kmp", "data.Repository")
        val untouched = templateArgsOf("data.Repository.repository", repository, mapOf("name" to "User"), OnExistingChoice.Fail)
        assertEquals(listOf("template", "onExisting", "name"), untouched.map { it.first })
        val changed = templateArgsOf("data.Repository.repository", repository, mapOf("name" to "User", "item" to "Int"), OnExistingChoice.Fail)
        assertEquals("item" to "Int", changed.last())
    }

    @Test
    fun `sample-jvmのcaptureを持つ役割はJSONの生成コマンドと同じ--argを組み値の入った生成先を予想する`() {
        val controller = detailOf("sample-jvm-with-captures", "api.Controller")
        val inputs = mapOf("resource" to "user", "name" to "User")

        val args = templateArgsOf("api.Controller", controller, inputs, OnExistingChoice.Fail)
        val exampleArgNames = Regex("""--arg (\w+)=""").findAll(controller.exampleCommand).map { it.groupValues[1] }.toList()
        assertEquals(exampleArgNames, (args - ("onExisting" to "fail")).map { it.first })
        assertEquals(
            ExpectedLocation.Known("src/main/kotlin/com/example/controller/user/UserController.kt"),
            expectedFilesOf(controller, inputs).single().location,
        )
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
        assertEquals(listOf("domain.Service"), loaded.snapshots.single().templates.map { it.template })
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
        // "name" moved from a required parameter to a required capture (design draft section 1),
        // so the missing-value message is now about a capture, not a parameter.
        val status = parseTemplateOutput(realOutput("missing-required-arg")).status as? TemplateRunStatus.Failed ?: throw AssertionError()
        assertNull(status.conflicting)
        assertTrue(status.body.first(), status.body.first().startsWith("The run gave no value for capture name"))
        assertTrue(status.body.any { "--arg name=<name>" in it })
    }

    @Test
    fun `実際の知らないarg名はkatachiのまとめまで届かずプロセッサの拒否に分類する`() = runBlocking {
        // An unknown `--arg template=` specifier is now caught and reported as this processor's
        // own `[FAILED]` (undeclaredArgNamesOf), not an uncaught crash -- so the "unreached katachi"
        // scenario is reproduced with the old, now-removed `roleName` key instead: a genuinely
        // unrecognised `--arg` name still crashes before any processor runs (checkNoUnknownArgs).
        //
        // TODO(template-per-file #19): this reuses the removed `roleName=` key to reach that crash,
        // which the IDE itself never sends any more -- it is not representative of what a real IDE
        // run can still hit (e.g. `--arg template=` naming a template a stale definition dropped).
        // A `real-jvm-*` fixture for that scenario still needs recording; left as a TODO since it
        // needs a real `katachiTemplate` run against a definition edited between two loads, which
        // this pass did not have the fixture-recording time for.
        val output = parseTemplateOutput(realOutput("unknown-arg"))
        assertFalse(output.reachedKatachi)
        assertEquals(TemplateRunStatus.Missing, output.status)

        val result = runOne("unknown-arg")
        val failure = ((result as? GenerationItemResult.Failed)?.failure as? GenerationFailure.NotReached)?.failure
        val rejected = failure as? GradleFailure.ProcessorRejected ?: throw AssertionError("$result")
        assertEquals("me.tbsten.katachi.processor.KatachiUnknownProcessorArgException", rejected.exceptionClassName)
        assertTrue(rejected.details.first().startsWith("Unknown processor argument(s): name, roleName."))
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
        assertTrue(body.first().contains("The run gave no value for capture name"))
    }

    private fun probeItem() = GenerationItem(
        templateIds = listOf(TemplateId(definition.id, "domain.Service")),
        module = definition,
        args = listOf("template" to "domain.Service", "name" to "IdePluginProbe"),
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
