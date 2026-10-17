package me.tbsten.katachi.intellij.ide.debug

import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.EditorNotificationPanel
import com.intellij.codeInsight.daemon.LineMarkerSettings
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.gutter.GutterMenu
import me.tbsten.katachi.intellij.ide.gutter.KatachiDeclarationLineMarkerProvider
import me.tbsten.katachi.intellij.ide.gutter.KatachiFileLineMarkerProvider
import me.tbsten.katachi.intellij.ide.gutter.RecordingAction
import me.tbsten.katachi.intellij.ide.gutter.annotator.AnnotatorTestBase
import me.tbsten.katachi.intellij.ide.gutter.redrawSeq
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.testing.AnnotationFixtures
import me.tbsten.katachi.intellij.testing.GutterFixtures
import me.tbsten.katachi.intellij.testing.declarationModel
import me.tbsten.katachi.intellij.testing.fileDeclarations
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleFileDeclarations
import me.tbsten.katachi.intellij.testing.roleModel

/**
 * The doors of `KatachiDebugBridge` the real-IDE smoke acts through: `describeBanners`, `applyFix`, `chooseFromGutterLater`,
 * `setGutterIcons` and `describeRedraw`. What `describeGutter` and the other readers say is in [DebugBridgeGutterTest].
 */
internal class DebugBridgeGutterActionsTest : AnnotatorTestBase() {
    private val service = roleModel("domain.Service", layoutEntry(GutterFixtures.SERVICE_PATH))
    private val serviceFile = "HealthService.kt"
    private val servicePath get() = lightPath(serviceFile).toString()

    private fun <T> withBridge(block: (KatachiDebugBridge) -> T): T {
        val previous = System.getProperty("katachi.debugBridge")
        System.setProperty("katachi.debugBridge", "true")
        try {
            return block(KatachiDebugBridge(project).also { it.onScreen = ::chainVisible })
        } finally {
            if (previous == null) System.clearProperty("katachi.debugBridge") else System.setProperty("katachi.debugBridge", previous)
        }
    }

    /** A headless test has no screen: a part is "on screen" when it and everything above it is visible. */
    private fun chainVisible(part: java.awt.Component): Boolean = generateSequence(part) { it.parent }.all { it.isVisible }

    override fun setUp() {
        super.setUp()
        useLocalFiles()
    }

    override fun tearDown() {
        try {
            LineMarkerSettings.getSettings().setEnabled(KatachiDeclarationLineMarkerProvider(), true)
        } finally {
            super.tearDown()
        }
    }

    private fun serviceReady() {
        val explanation = roleFileDeclarations(
            service,
            fileDeclarations(GutterFixtures.SERVICE_PATH, declarationModel(SourceDeclarationKind.Class, "HealthService", 7, rules = listOf(GutterFixtures.SERVICE_RULE))),
        )
        ready(serviceFile, fileRoles(GutterFixtures.SERVICE_PATH, service), listOf(explanation))
    }

    private fun editorOfOpenFile(): FileEditor = FileEditorManager.getInstance(project).getEditors(myFixture.file.virtualFile).first()

    /**
     * Puts a banner over the open editor the way the real editor does, inside the editor's own component (a headless editor has no parent above it): the
     * test editor manager's `addTopComponent` keeps the part to itself and draws it nowhere. That a real IDE draws it there is
     * for the real-IDE test to show.
     */
    private fun addBanner(text: String, vararg links: String): EditorNotificationPanel {
        val editor = editorOfOpenFile()
        val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Warning).text(text)
        links.forEach { panel.createActionLabel(it) {} }
        editor.component.add(panel)
        return panel
    }

    private fun annotatedFile() {
        definitions.resolveWith(DeclarationSiteRef("ServiceRole.kt", 94), ResolvedSite.Found(myFixture.addFileToProject("ServiceRole.kt", "// role\n".repeat(100)).virtualFile, 94))
        serviceReady()
        open(serviceFile, "package a\n\ninternal class HealthService\n")
        checkRan(listOf(AnnotationFixtures.unsatisfied(lightPath(serviceFile), "HealthService", line = 3)))
        annotated()
    }

    fun `test describeBanners は katachi の banner の文言とリンクを 並びのまま返す`() {
        serviceReady()
        open(serviceFile, "package a\n")
        addBanner("katachi: [UnsatisfiedFileConstraint] Missing: class A", "Go to the rule", "Run the check again")

        val lines = withBridge { it.describeBanners(servicePath) }.lines()

        assertEquals(listOf("banners=$servicePath", "count=1", "banner=katachi: [UnsatisfiedFileConstraint] Missing: class A|Go to the rule;Run the check again"), lines)
    }

    fun `test describeBanners は katachi でないパネルと 表示されていないパネルを 数えない`() {
        serviceReady()
        open(serviceFile, "package a\n")
        addBanner("Some other plugin's notice", "Do it")
        addBanner("katachi: hidden").isVisible = false
        addBanner("katachi: shown")

        val lines = withBridge { it.describeBanners(servicePath) }.lines()

        assertEquals(listOf("banners=$servicePath", "count=1", "banner=katachi: shown|"), lines)
    }

    fun `test describeBanners は 他のファイルの editor を含む親より上の panel を 数えない`() {
        serviceReady()
        val a = open("A.kt", "package a\n")
        val editorA = FileEditorManager.getInstance(project).getEditors(a.virtualFile).first()
        val b = open("B.kt", "package b\n")
        val editorB = FileEditorManager.getInstance(project).getEditors(b.virtualFile).first()
        // A split: one parent holds both editors, and a panel under B's own part only.
        val split = javax.swing.JPanel()
        val partB = javax.swing.JPanel().apply { add(editorB.component) }
        split.add(editorA.component)
        split.add(partB)
        partB.add(EditorNotificationPanel(editorB, EditorNotificationPanel.Status.Warning).text("katachi: B"))

        val ofA = withBridge { it.describeBanners(lightPath("A.kt").toString()) }
        val ofB = withBridge { it.describeBanners(lightPath("B.kt").toString()) }

        assertEquals("count=0", ofA.lines()[1])
        assertEquals("count=1", ofB.lines()[1])
    }

    fun `test describeBanners は banner の無いファイルと 開いていないファイルに 0 件を返す`() {
        serviceReady()
        open(serviceFile, "package a\n")
        assertEquals("banners=$servicePath\ncount=0", withBridge { it.describeBanners(servicePath) })
        assertEquals("banners=/nowhere/A.kt\ncount=0", withBridge { it.describeBanners("/nowhere/A.kt") })
    }

    fun `test applyFix は 行の下線の Go to the rule を選ぶと 規則の行が開く`() {
        annotatedFile()

        val answer = withBridge { it.applyFix(servicePath, 3, "Go to the rule") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals("applied=Go to the rule (ServiceRole.kt:94)", answer)
        val opened = FileEditorManager.getInstance(project).selectedEditors.first()
        assertEquals("ServiceRole.kt", opened.file?.name)
        assertEquals(94, FileEditorManager.getInstance(project).selectedTextEditor!!.caretModel.logicalPosition.line + 1)
        assertEquals("caret=ServiceRole.kt|94", withBridge { it.describeCaret() })
    }

    fun `test applyFix は Run the check again を選ぶと 偽物のアクションが 1 回呼ばれる`() {
        val run = RecordingAction("Run the check")
        actions[GutterMenu.RUN_CHECK] = run
        annotatedFile()

        val answer = withBridge { it.applyFix(servicePath, 3, "Run the check") }
        assertEquals("the fix runs later, not inside the call", 0, run.events.size)
        assertEquals("applied=Run the check again", answer)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(1, run.events.size)
    }

    fun `test applyFix は 無い文言 無い行 開いていないファイルと 使えない fix を選ばない`() {
        annotatedFile()

        assertEquals("applied=none", withBridge { it.applyFix(servicePath, 3, "No such fix") })
        assertEquals("applied=none", withBridge { it.applyFix(servicePath, 1, "Go to the rule") })
        assertEquals("applied=none", withBridge { it.applyFix("/nowhere/A.kt", 3, "Go") })
        // The action is not registered, so "Run the check again" is not available.
        assertEquals("applied=none", withBridge { it.applyFix(servicePath, 3, "Run the check") })
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals("a refused fix opens nothing", serviceFile, FileEditorManager.getInstance(project).selectedEditors.first().file?.name)
    }

    fun `test chooseFromGutterLater は 選んだ直後は行を実行せず EDT を回すと実行する`() {
        val refresh = RecordingAction("Refresh")
        actions[GutterMenu.RUN_CHECK] = RecordingAction("Run the check")
        actions[GutterMenu.REFRESH] = refresh
        serviceReady()
        drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)

        assertEquals("chosen=⟳ Reload Definition", withBridge { it.chooseFromGutterLater(servicePath, 7, "⟳ Reload") })
        assertEquals("not yet", 0, refresh.events.size)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(1, refresh.events.size)

        assertEquals("chosen=none", withBridge { it.chooseFromGutterLater(servicePath, 7, "◆ Allowed by") })
        assertEquals("chosen=none", withBridge { it.chooseFromGutterLater(servicePath, 2, "⟳") })
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(1, refresh.events.size)
    }

    fun `test setGutterIcons は Kotlin と md の両方の ◆ を 消して 戻す`() {
        serviceReady()
        assertEquals(1, drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT).size)

        assertEquals("gutterIcons=false", withBridge { it.setGutterIcons(false) })
        assertEquals(emptyList<Any>(), redrawnByRefresher())

        assertEquals("gutterIcons=true", withBridge { it.setGutterIcons(true) })
        assertEquals(1, redrawnByRefresher().size)
    }

    fun `test setGutterIcons は 1 つの ID を共有する Kotlin 以外の provider の ◆ も 消して 戻す`() {
        LineMarkerProviders.getInstance().addExplicitExtension(Language.ANY, KatachiFileLineMarkerProvider { ports() }, testRootDisposable)
        val adr = roleModel("docs.Adr", layoutEntry("docs/a.md"))
        ready("a.md", fileRoles("docs/a.md", adr))
        assertEquals(1, drawn("a.md", "# a\n").size)

        withBridge { it.setGutterIcons(false) }
        assertEquals(emptyList<Any>(), redrawnByRefresher())

        withBridge { it.setGutterIcons(true) }
        assertEquals(1, redrawnByRefresher().size)
    }

    fun `test describeRedraw は 最後の描き直しの番号と理由と状態の型と busy を返す`() {
        serviceReady()
        drawn(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)
        val before = redrawSeq(project)
        withBridge { it.setGutterIcons(false) }
        redrawnByRefresher()

        val lines = withBridge { it.describeRedraw() }.lines()
        val redraw = lines[0].removePrefix("redraw=").split("|")
        assertTrue(lines.toString(), redraw[0].toLong() > before)
        assertTrue(lines.toString(), "gutter icons off" in redraw[1])
        assertEquals("Ready", redraw[2])
        assertEquals("busy=false", lines[1])
    }

    fun `test 新しい口は デバッグ用の入口が無効なときは動かない`() {
        val bridge = KatachiDebugBridge(project)
        assertThrows(IllegalStateException::class.java) { bridge.describeBanners(servicePath) }
        assertThrows(IllegalStateException::class.java) { bridge.applyFix(servicePath, 1, "x") }
        assertThrows(IllegalStateException::class.java) { bridge.chooseFromGutterLater(servicePath, 1, "x") }
        assertThrows(IllegalStateException::class.java) { bridge.fillDialog("name", "x") }
        assertThrows(IllegalStateException::class.java) { bridge.pressDialog("generate") }
        assertThrows(IllegalStateException::class.java) { bridge.setGutterIcons(false) }
        assertThrows(IllegalStateException::class.java) { bridge.describeRedraw() }
    }
}
