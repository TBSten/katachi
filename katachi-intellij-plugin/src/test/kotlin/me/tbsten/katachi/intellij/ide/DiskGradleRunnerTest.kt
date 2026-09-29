package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import me.tbsten.katachi.intellij.data.json.parseTemplateDescriptionJson
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.testing.ContractFixtures
import java.nio.file.Files
import java.nio.file.Path

/** The test helpers of the IDE tests themselves: the fake Gradle, the source roots and the unsaved Document. */
internal class DiskGradleRunnerTest : KatachiIdeTestBase() {
    private val json get() = root.resolve("arch-a").resolve(KatachiModule.TEMPLATE_DESCRIPTION_JSON)

    private fun loadRequest(vararg gradlePaths: String) = GradleRunRequest(
        root,
        gradlePaths.map { GradleTaskInvocation("$it:${KatachiModule.TEMPLATES_JSON_TASK}") },
    )

    private fun generateRequest(vararg args: Pair<String, String>, module: String = ":arch-a") = GradleRunRequest(
        root,
        listOf(GradleTaskInvocation("$module:${KatachiModule.TEMPLATE_TASK}", args.toList())),
    )

    private fun runOnce(request: GradleRunRequest, lines: MutableList<String> = mutableListOf()): GradleRunOutcome = runBlocking {
        gradle.run(request, object : GradleRunListener {
            override fun onLine(line: String) {
                lines += line
            }
        })
    }

    private fun templatesOnDisk(): List<String> = parseTemplateDescriptionJson(Files.readString(json), json).map { it.template }

    // covers: 論点1
    fun `test 読み込みのたびに積んだ JSON を順に書き、尽きたらモジュールの見本に戻る`() {
        gradle.loads += LoadAnswer(json = """{"templates":[],"details":[]}""")
        gradle.loads += LoadAnswer(json = ContractFixtures.json("arch-b"))

        assertEquals(GradleRunOutcome.Succeeded, runOnce(loadRequest(":arch-a")))
        assertEquals(emptyList<String>(), templatesOnDisk())

        assertEquals(GradleRunOutcome.Succeeded, runOnce(loadRequest(":arch-a")))
        assertEquals(templatesOf("arch-b"), templatesOnDisk())

        assertEquals(GradleRunOutcome.Succeeded, runOnce(loadRequest(":arch-a")))
        assertEquals(templatesOf("arch-a"), templatesOnDisk())
        assertEquals(3, gradle.loadRequests.size)
        assertEquals(emptyList<GradleRunRequest>(), gradle.generationRequests)
    }

    // covers: 論点1
    fun `test 読み込みに失敗を積むと何も書かずに失敗し、次の読み込みは戻る`() {
        gradle.loads += LoadAnswer(fails = true)
        val lines = mutableListOf<String>()

        assertEquals(GradleRunOutcome.Failed, runOnce(loadRequest(":arch-a"), lines))
        assertFalse(Files.exists(json))
        assertTrue(lines.any { "BUILD FAILED" in it })

        assertEquals(GradleRunOutcome.Succeeded, runOnce(loadRequest(":arch-a")))
        assertTrue(Files.exists(json))
    }

    // covers: 論点1
    fun `test 割り当てを上書きしたモジュールだけ別の契約 JSON を書く`() {
        gradle.fixtureOf["arch-a"] = "sample-android-with-captures"

        runOnce(loadRequest(":arch-a", ":arch-b"))

        assertEquals(templatesOf("sample-android-with-captures"), templatesOnDisk())
        val other = root.resolve("arch-b").resolve(KatachiModule.TEMPLATE_DESCRIPTION_JSON)
        assertEquals(templatesOf("arch-b"), parseTemplateDescriptionJson(Files.readString(other), other).map { it.template })
    }

    // covers: 論点1
    fun `test 頼まれた template と args から出力先を決めて指定の中身を書く`() {
        gradle.plans += PlannedGeneration { call ->
            mapOf(call.moduleDir.resolve("src/${call.arg("name")}.kt") to "// ${call.template}\n")
        }
        val lines = mutableListOf<String>()

        val outcome = runOnce(generateRequest("template" to "feature.Screen", "name" to "Home", "name" to "Detail"), lines)

        val written = root.resolve("arch-a/src/Detail.kt")
        assertEquals(GradleRunOutcome.Succeeded, outcome)
        assertEquals("// feature.Screen\n", Files.readString(written))
        assertTrue(lines.any { it.contains("[template] Wrote ${written.toUri()}") })
    }

    // covers: 論点1
    fun `test 相対パスはプロジェクトのルートからで、計画が尽きたら契約の出力に戻る`() {
        gradle.plans += PlannedGeneration { mapOf(Path.of("x/y/A.kt") to "a") }

        runOnce(generateRequest())
        runOnce(generateRequest())

        assertEquals("a", Files.readString(root.resolve("x/y/A.kt")))
        assertEquals("// written by the fake Gradle\n", Files.readString(root.resolve("data/src/main/kotlin/com/example/data/UserRepository.kt")))
    }

    // covers: 論点1
    fun `test 失敗を指定した生成は何も書かずに失敗する`() {
        gradle.plans += PlannedGeneration(fails = true) { mapOf(Path.of("A.kt") to "a") }

        val outcome = runOnce(generateRequest())

        assertEquals(GradleRunOutcome.Failed, outcome)
        assertFalse(Files.exists(root.resolve("A.kt")))
    }

    // covers: 論点1
    fun `test gate を指定した生成は出力を出したまま止まり、開けると書く`() {
        val gate = CompletableDeferred<Unit>()
        val plan = PlannedGeneration(gate = gate) { mapOf(Path.of("A.kt") to "a") }
        gradle.plans += plan
        val lines = mutableListOf<String>()

        val running = scope.launch { runOnce2(generateRequest(), lines) }
        runBlocking { plan.reachedGate.await() }

        assertFalse(Files.exists(root.resolve("A.kt")))
        assertTrue(lines.any { "Wrote" in it })
        gate.complete(Unit)
        runBlocking { running.join() }
        assertEquals("a", Files.readString(root.resolve("A.kt")))
    }

    // covers: 論点1
    fun `test gate で止まった生成を取り消すと cancelled が立ち何も書かない`() {
        val plan = PlannedGeneration(gate = CompletableDeferred()) { mapOf(Path.of("A.kt") to "a") }
        gradle.plans += plan

        val running = scope.launch { runOnce2(generateRequest(), mutableListOf()) }
        runBlocking { plan.reachedGate.await() }
        runBlocking { running.cancelAndJoin() }

        assertTrue(gradle.cancelled)
        assertFalse(Files.exists(root.resolve("A.kt")))
    }

    // covers: 論点16
    fun `test ソースルートに登録したディレクトリは解除するとコンテンツルートごと消える`() {
        val before = ModuleRootManager.getInstance(module).contentRootUrls.toList()

        val src = addSourceRoot(root.resolve("feature/src/main/kotlin"))

        assertTrue(src.url in ModuleRootManager.getInstance(module).contentRootUrls)
        releaseSourceRoots()
        assertEquals(before, ModuleRootManager.getInstance(module).contentRootUrls.toList())
    }

    // covers: 論点16
    fun `test 解除は何度呼んでも壊れず、登録していなければ何もしない`() {
        releaseSourceRoots()
        addSourceRoot(root.resolve("a/src"))
        addSourceRoot(root.resolve("b/src"), packagePrefix = "org.b")

        releaseSourceRoots()
        releaseSourceRoots()

        assertEquals(emptyList<String>(), ModuleRootManager.getInstance(module).contentRootUrls.filter { root.fileName.toString() in it })
    }

    // covers: 論点2
    fun `test 未保存の Document はディスクと違う中身を持ち、保存されていない`() {
        val path = root.resolve("feature/Home.kt")

        val document = unsavedDocument(path, onDisk = "package a\n", inMemory = "// typed\n")

        assertEquals("// typed\n", document.text)
        assertEquals("package a\n", Files.readString(path))
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        assertNotNull(LocalFileSystem.getInstance().findFileByNioFile(path))
    }

    private suspend fun runOnce2(request: GradleRunRequest, lines: MutableList<String>) {
        gradle.run(request, object : GradleRunListener {
            override fun onLine(line: String) {
                synchronized(lines) { lines += line }
            }
        })
    }

    private fun templatesOf(fixture: String): List<String> = ContractFixtures.templates(fixture).map { it.template }
}
