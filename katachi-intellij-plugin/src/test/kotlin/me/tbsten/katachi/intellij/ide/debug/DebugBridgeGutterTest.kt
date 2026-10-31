package me.tbsten.katachi.intellij.ide.debug

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.gutter.GutterMenu
import me.tbsten.katachi.intellij.ide.gutter.RecordingAction
import me.tbsten.katachi.intellij.ide.gutter.annotator.AnnotatorTestBase
import me.tbsten.katachi.intellij.ide.gutter.shown
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.testing.AnnotationFixtures
import me.tbsten.katachi.intellij.testing.GutterFixtures
import me.tbsten.katachi.intellij.testing.declarationModel
import me.tbsten.katachi.intellij.testing.fileDeclarations
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleFileDeclarations
import me.tbsten.katachi.intellij.testing.roleModel

/**
 * `KatachiDebugBridge.describeGutter`, `describeHighlights`, `clickGutter` and `chooseFromGutter`: the String door the Driver
 * smoke reads the ◆, the underlines and the ◆'s menu through. The files are on the local disk (the bridge finds a file by its
 * path), and what is read is what the real daemon drew for them.
 */
internal class DebugBridgeGutterTest : AnnotatorTestBase() {
    private val service = roleModel("domain.Service", layoutEntry(GutterFixtures.SERVICE_PATH))
    private val serviceFile = "HealthService.kt"
    private val servicePath get() = lightPath(serviceFile).toString()

    private fun <T> withBridge(block: (KatachiDebugBridge) -> T): T {
        val previous = System.getProperty("katachi.debugBridge")
        System.setProperty("katachi.debugBridge", "true")
        try {
            return block(KatachiDebugBridge(project))
        } finally {
            if (previous == null) System.clearProperty("katachi.debugBridge") else System.setProperty("katachi.debugBridge", previous)
        }
    }

    override fun setUp() {
        super.setUp()
        useLocalFiles()
    }

    private fun serviceReady(rule: RuleRef = GutterFixtures.SERVICE_RULE) {
        val explanation = roleFileDeclarations(
            service,
            fileDeclarations(GutterFixtures.SERVICE_PATH, declarationModel(SourceDeclarationKind.Class, "HealthService", 7, rules = listOf(rule))),
        )
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), listOf(explanation))
    }

    // covers: 論点17
    fun `test describeGutter は daemon が描いた ◆ を 行 メニューの有無 葉 ツールチップで返す`() {
        serviceReady()
        drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)

        val lines = withBridge { it.describeGutter(servicePath) }.lines()

        assertEquals(
            listOf(
                "gutter=$servicePath",
                "open=true",
                "marks=1",
                "mark=7|menu|HealthService|katachi: domain.Service — allowed by class HealthService (ServiceRole.kt:80)",
            ),
            lines,
        )
    }

    // covers: 論点17
    fun `test describeGutter は 開いていないファイルは open=false 規則の山括弧とアンパサンドは 生のまま 改行は エスケープして 1 行で返す`() {
        assertEquals("gutter=/nowhere/A.kt\nopen=false", withBridge { it.describeGutter("/nowhere/A.kt") })

        serviceReady(RuleRef("class Box<T> & List<String>", DeclarationSiteRef("ServiceRole.kt", 80)))
        drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)
        assertEquals(
            "mark=7|menu|HealthService|katachi: domain.Service — allowed by class Box<T> & List<String> (ServiceRole.kt:80)",
            withBridge { it.describeGutter(servicePath) }.lines().last(),
        )

        val failure = StaleReason.ReloadFailed(LoadFailure.Gradle(GradleFailure.CompilationFailed(emptyList())))
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Stale(snapshot.loadedAt, failure)))
        redrawn()
        val described = withBridge { it.describeGutter(servicePath) }
        assertTrue(described, described.contains("(stale)\\nThe definition could not be read again: the definition does not compile"))
        assertEquals("one line per mark", 4, described.lines().size)
    }

    // covers: 論点17
    fun `test describeHighlights は 下線の 重さ 範囲 説明 直し方を返し ファイルの上の注釈は range が file`() {
        definitions.resolveWith(DeclarationSiteRef("ServiceRole.kt", 94), ResolvedSite.Found(myFixture.addFileToProject("ServiceRole.kt", "// role\n".repeat(100)).virtualFile, 94))
        serviceReady()
        open(serviceFile, "package a\n\ninternal class HealthService\n")
        checkRan(
            listOf(
                AnnotationFixtures.unsatisfied(lightPath(serviceFile), "HealthService", line = 3),
                AnnotationFixtures.missing(lightPath(serviceFile)),
            ),
        )
        annotated()

        val lines = withBridge { it.describeHighlights(servicePath) }.lines()

        assertEquals(listOf("highlights=$servicePath", "open=true", "count=2"), lines.take(3))
        val banner = lines.first { "|file|" in it }
        assertTrue(banner, banner.startsWith("highlight=ERROR|file|katachi: [UnsatisfiedFileConstraint] Missing: class DefaultGetUserUseCase : GetUserUseCase (UseCaseRole.kt:20)"))
        val ranged = lines.first { "|3:" in it }
        assertTrue(ranged, ranged.startsWith("highlight=ERROR|3:16-3:29|katachi: \"${AnnotationFixtures.CONSTRAINT}\" (domain.Service) rejected HealthService — declared at ServiceRole.kt:94\\n"))
        assertTrue(ranged, ranged.endsWith("|Go to the rule (ServiceRole.kt:94);Run the check again"))
    }

    // The Driver smoke calls the bridge from a thread of its own: the banner is read off the EDT's editors, so the call must hop there.
    fun `test describeHighlights は EDT でないスレッドから呼んでも ファイルの上の注釈を返す`() {
        serviceReady()
        open(serviceFile, "package a\n\ninternal class HealthService\n")
        checkRan(listOf(AnnotationFixtures.missing(lightPath(serviceFile))))
        annotated()

        val future = ApplicationManager.getApplication().executeOnPooledThread<String> {
            check(!ApplicationManager.getApplication().isDispatchThread) { "the call must come from a pooled thread" }
            withBridge { it.describeHighlights(servicePath) }
        }
        val answer = PlatformTestUtil.waitForFuture(future, 10_000)

        val lines = answer.lines()
        assertEquals(listOf("highlights=$servicePath", "open=true", "count=1"), lines.take(3))
        assertTrue(answer, lines.last().startsWith("highlight=ERROR|file|katachi: [UnsatisfiedFileConstraint] Missing: class DefaultGetUserUseCase"))
    }

    // covers: 論点17
    fun `test describeHighlights は 検査の無いファイルや 開いていないファイルには 0 件か open=false`() {
        serviceReady()
        open(serviceFile, "package a\n")
        assertEquals(listOf("highlights=$servicePath", "open=true", "count=0"), withBridge { it.describeHighlights(servicePath) }.lines())
        assertEquals("highlights=/nowhere/A.kt\nopen=false", withBridge { it.describeHighlights("/nowhere/A.kt") })
    }

    private fun withMenuActions() {
        actions[GutterMenu.RUN_CHECK] = RecordingAction("Run the check")
        actions[GutterMenu.REFRESH] = RecordingAction("Refresh")
    }

    // covers: 論点17
    fun `test clickGutter は ◆ の押したメニューの見出しと 行を 並びのまま返し 閉じる`() {
        withMenuActions()
        val role = myFixture.addFileToProject("ServiceRole.kt", "// role\n".repeat(100)).virtualFile
        definitions.resolveWith(DeclarationSiteRef("ServiceRole.kt", 80), ResolvedSite.Found(role, 80))
        definitions.resolveWith(DeclarationSiteRef("ServiceRole.kt", 1), ResolvedSite.Found(role, 1))
        serviceReady()
        drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)

        val lines = withBridge { it.clickGutter(servicePath, 7) }.lines()

        assertEquals("menu=HealthService — domain.Service", lines.first())
        assertEquals(
            listOf(
                "item=◆ Allowed by class HealthService|ServiceRole.kt:80|enabled",
                "item=◇ Role domain.Service|ServiceRole.kt:1|enabled",
                "section=Other files of domain.Service",
                "item=— No other file yet||disabled",
                "separator",
                "item=▶ Run the check (:arch:test)||enabled",
                "item=⟳ Reload Definition||enabled",
            ),
            lines.drop(1).map { it.replace(Regex("^(item=◇ Role domain.Service) — [^|]*"), "$1") },
        )
        assertEquals("the menu is the one the ◆ holds", menus().single().shown().size, lines.drop(1).size)
    }

    // covers: 論点17
    fun `test clickGutter は 宣言元が見つからない行を 押せない行として 理由を右に書いて返す`() {
        withMenuActions()
        serviceReady()
        drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)
        val lines = withBridge { it.clickGutter(servicePath, 7) }.lines()
        assertTrue(lines.toString(), lines.any { it.startsWith("item=◆ Allowed by class HealthService|(declaration not found: ") && it.endsWith("|disabled") })
    }

    // covers: 論点17
    fun `test clickGutter は ◆ の無い行 開いていないファイルには menu=none`() {
        serviceReady()
        drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)
        assertEquals("menu=none", withBridge { it.clickGutter(servicePath, 2) })
        assertEquals("menu=none", withBridge { it.clickGutter("/nowhere/A.kt", 7) })
    }

    // covers: 論点17
    fun `test chooseFromGutter は 行の先頭の文字で選び 押せない行や無い行は none`() {
        val refresh = RecordingAction("Refresh")
        actions[GutterMenu.RUN_CHECK] = RecordingAction("Run the check")
        actions[GutterMenu.REFRESH] = refresh
        serviceReady()
        drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)

        assertEquals("chosen=⟳ Reload Definition", withBridge { it.chooseFromGutter(servicePath, 7, "⟳ Reload") })
        assertEquals(1, refresh.events.size)
        assertEquals("the file and the project reach the action as a click would give them", servicePath, refresh.events.single().dataContext.let {
            com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE.getData(it)?.path
        })

        assertEquals("a row that cannot be chosen is not chosen", "chosen=none", withBridge { it.chooseFromGutter(servicePath, 7, "◆ Allowed by") })
        assertEquals("chosen=none", withBridge { it.chooseFromGutter(servicePath, 7, "no such row") })
        assertEquals("chosen=none", withBridge { it.chooseFromGutter(servicePath, 2, "⟳") })
        assertEquals(1, refresh.events.size)
    }

    // covers: 論点17
    fun `test デバッグ用の入口が無効なときは ◆ も下線も読めない`() {
        val bridge = KatachiDebugBridge(project)
        assertThrows(IllegalStateException::class.java) { bridge.describeGutter(servicePath) }
        assertThrows(IllegalStateException::class.java) { bridge.describeHighlights(servicePath) }
        assertThrows(IllegalStateException::class.java) { bridge.clickGutter(servicePath, 1) }
        assertThrows(IllegalStateException::class.java) { bridge.chooseFromGutter(servicePath, 1, "x") }
    }
}
