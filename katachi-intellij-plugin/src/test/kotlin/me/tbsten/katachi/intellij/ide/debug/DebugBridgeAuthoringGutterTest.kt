package me.tbsten.katachi.intellij.ide.debug

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import java.util.concurrent.Callable
import me.tbsten.katachi.intellij.ide.authoring.gutter.DefinitionGutterMenu
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.ide.authoring.AuthoringPorts
import me.tbsten.katachi.intellij.ide.authoring.completion.AuthoringFixtures
import me.tbsten.katachi.intellij.ide.authoring.gutter.DefinitionGutterTestBase
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness

/**
 * `describeDefinitionGutter` and `describeCompletion` of the debug bridge, the Strings the Driver smoke of ide-authoring reads the
 * definition side through. The definition file is a file on the disk (the bridge finds a file by its path), the ◆ are the ones the real
 * daemon drew, and the definition is the fake service's.
 */
internal class DebugBridgeAuthoringGutterTest : DefinitionGutterTestBase() {
    private val path get() = lightPath(ROLE_FILE).toString()

    private fun reader() = AuthoringDebugReader(project, portsOf = { ports() })

    private fun <T> withBridge(block: (KatachiDebugBridge) -> T): T {
        val previous = System.getProperty("katachi.debugBridge")
        System.setProperty("katachi.debugBridge", "true")
        try {
            return block(KatachiDebugBridge(project))
        } finally {
            if (previous == null) System.clearProperty("katachi.debugBridge") else System.setProperty("katachi.debugBridge", previous)
        }
    }

    private fun drawnDefinition() {
        useLocalFiles()
        writeFiles()
        openDefinition()
        readDefinition()
        definitionMarks()
    }

    fun `test describeDefinitionGutter は daemon が描いた ◆ と その メニューの行 それに Code Vision を行ごとに返す`() {
        drawnDefinition()

        val lines = reader().describeDefinitionGutter(path).lines()

        assertEquals(listOf("definitionGutter=$path", "open=true", "marks=2"), lines.take(3))
        assertEquals("mark=2|\"|katachi: domain.Service — 3 files", lines[3])
        assertEquals("item=HealthService.kt|core/service|enabled", lines[4])
        val markOfTemplate = lines.indexOfFirst { it.startsWith("mark=4|") }
        assertEquals("mark=4|template|katachi: generate from domain.Service", lines[markOfTemplate])
        assertEquals(listOf("item=+ Generate from this template…||enabled", "item=⊞ Show in the tool window||enabled"), lines.subList(markOfTemplate + 1, markOfTemplate + 3))
        assertEquals(listOf("lenses=1", "lens=3|3 files"), lines.takeLast(2))
    }

    fun `test 定義ファイル自身が役割に当たり ソース側の ◆ も描かれていても 定義側の ◆ だけを返す`() {
        useLocalFiles()
        writeFiles()
        openDefinition()
        readDefinition()
        // The role holds ServiceRole.kt itself (sample's `"roles" / "*Role.kt"`), so the daemon also draws the source side's ◆ on it.
        definitions.answer(lightPath(ROLE_FILE), fileRoles(ROLE_FILE, serviceRole()))
        definitionMarks()
        val sourceSide = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project).filter { it.navigationHandler !is DefinitionGutterMenu }
        assertTrue("the fixture draws no source-side ◆", sourceSide.isNotEmpty())

        val lines = reader().describeDefinitionGutter(path).lines()

        assertEquals("marks=2", lines[2])
        val marks = lines.filter { it.startsWith("mark=") }
        assertEquals(marks.toString(), listOf("mark=2|\"|katachi: domain.Service — 3 files", "mark=4|template|katachi: generate from domain.Service"), marks)
        // None of the source side's rows (its tooltips and its menu) is among what the definition side lists.
        sourceSide.forEach { info -> assertFalse(lines.toString(), lines.any { it == "mark=${myFixture.editor.document.getLineNumber(info.startOffset) + 1}|${info.element?.text.orEmpty()}|${info.lineMarkerTooltip.orEmpty()}" }) }
    }

    fun `test describeDefinitionGutter は EDT の外のスレッドから呼んでも 同じ行を返す`() {
        drawnDefinition()
        val onEdt = reader().describeDefinitionGutter(path)

        val future = ApplicationManager.getApplication().executeOnPooledThread(Callable { reader().describeDefinitionGutter(path) })
        val offEdt = PlatformTestUtil.waitForFuture(future, 30_000)

        assertEquals(onEdt, offEdt)
        assertTrue(offEdt, offEdt.lines().any { it.startsWith("mark=2|") })
    }

    fun `test describeDefinitionGutter は 開いていないファイルと 定義ファイルでないファイルには open=false か 何も無し`() {
        assertEquals("definitionGutter=/nowhere/A.kt\nopen=false", reader().describeDefinitionGutter("/nowhere/A.kt"))
        useLocalFiles()
        open("Plain.kt", "class Plain\n")
        val lines = reader().describeDefinitionGutter(lightPath("Plain.kt").toString()).lines()
        assertEquals(listOf("open=true", "marks=0", "lenses=0"), lines.drop(1))
    }

    fun `test 答えが古いときは ◆ の tooltip も Code Vision も stale を付けて返す`() {
        useLocalFiles()
        writeFiles()
        openDefinition()
        readDefinition(freshness = Freshness.Stale(java.time.Instant.EPOCH, me.tbsten.katachi.intellij.model.StaleReason.DefinitionChanged))
        definitionMarks()

        val lines = reader().describeDefinitionGutter(path).lines()

        assertTrue(lines.toString(), lines.any { it == "mark=2|\"|katachi: domain.Service — 3 files (stale)" })
        assertEquals("lens=3|3 files (stale)", lines.last())
    }

    fun `test describeCompletion は 引用符の中の場所と 候補を 出す順に返す`() {
        val service = definitions
        project.replaceService(AuthoringPorts::class.java, AuthoringPorts { service }, testRootDisposable)
        useLocalFiles()
        val text = AuthoringFixtures.inFunction("\"*Screen.kt\".file().template {\n    captureValue(\"\")\n}")
        val file = open("ScreenRole.kt", text)
        service.resolveWith(DeclarationSiteRef("ScreenRole.kt", 2), ResolvedSite.Found(file.virtualFile, 2))
        service.emit(DefinitionState.Ready(listOf(AuthoringFixtures.snapshotWithSiteAt(2)), Freshness.Fresh))
        val offset = text.indexOf("captureValue(\"") + "captureValue(\"".length

        val lines = reader().describeCompletion(lightPath("ScreenRole.kt").toString(), offset).lines()

        assertEquals("completion=${lightPath("ScreenRole.kt")}|$offset", lines[0])
        assertEquals(listOf("open=true", "site=CaptureValue(2)", "candidates=3"), lines.subList(1, 4))
        assertEquals(listOf("feature", "name", "nameOf"), lines.drop(4).map { it.removePrefix("candidate=").substringBefore('|') })
    }

    fun `test describeCompletion は 補完の場所でない所では site=none で 候補 0`() {
        useLocalFiles()
        open("ScreenRole.kt", "fun f() {\n    val x = \"plain\"\n}\n")

        val lines = reader().describeCompletion(lightPath("ScreenRole.kt").toString(), 25).lines()

        assertEquals(listOf("site=none", "candidates=0"), lines.drop(2))
        assertEquals("completion=/nowhere/A.kt|0\nopen=false", reader().describeCompletion("/nowhere/A.kt", 0))
    }

    fun `test 橋の 3 つの口は 橋が切れているときは 使えず 入っているときは 読み取りの結果を返す`() {
        drawnDefinition()

        val refused = runCatching { KatachiDebugBridge(project).describeDefinitionGutter(path) }.exceptionOrNull()
        assertTrue(refused.toString(), refused is IllegalStateException)
        assertTrue(runCatching { KatachiDebugBridge(project).describeCompletion(path, 0) }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { KatachiDebugBridge(project).describeDefinitionErrors(path) }.exceptionOrNull() is IllegalStateException)

        withBridge { bridge ->
            assertEquals("definitionGutter=$path", bridge.describeDefinitionGutter(path).lines().first())
            assertEquals("completion=$path|0", bridge.describeCompletion(path, 0).lines().first())
            assertEquals("definitionErrors=$path", bridge.describeDefinitionErrors(path).lines().first())
        }
    }
}
