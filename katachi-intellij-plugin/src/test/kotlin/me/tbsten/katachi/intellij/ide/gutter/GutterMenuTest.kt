package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.LoggedErrorProcessor.Action
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.testing.GutterFixtures
import me.tbsten.katachi.intellij.testing.declarationModel
import me.tbsten.katachi.intellij.testing.fileDeclarations
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleFileDeclarations
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.templateRef
import me.tbsten.katachi.intellij.testing.withTemplates
import java.nio.file.Files
import java.nio.file.Path

/**
 * V2 of the gutter's P2: the menu a ◆ opens, over the real daemon and PSI with the foundation's fakes. What the rows say,
 * and what choosing each does: open a file or a line of the definition, hand the next file to the generate dialog, call the
 * foundation's actions.
 */
internal class GutterMenuTest : GutterTestBase() {
    private val fileName = "HealthService.kt"
    private val legacy = "src/main/kotlin/com/example/service/LegacyStatusService.kt"
    private val base = roleModel("domain.Service", layoutEntry(GutterFixtures.SERVICE_PATH, matchedFiles = listOf(GutterFixtures.SERVICE_PATH, legacy)))
    private val service = base.withTemplates(templateRef(base, "service"))
    private val templateId get() = TemplateId(module(":arch").id, "domain.Service.service")
    private lateinit var root: Path

    override fun setUp() {
        super.setUp()
        root = Files.createTempDirectory("katachi-gutter-menu").toRealPath()
        // The sibling files are real files on disk, outside the project the test fixture owns.
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString())
    }

    override fun tearDown() {
        try {
            FileUtil.delete(root.toFile())
        } finally {
            super.tearDown()
        }
    }

    private fun explained() = listOf(
        roleFileDeclarations(
            service,
            fileDeclarations(
                GutterFixtures.SERVICE_PATH,
                declarationModel(SourceDeclarationKind.Class, "HealthService", 7, rules = listOf(GutterFixtures.SERVICE_RULE)),
            ),
        ).let { it.copy(constraint = it.constraint.copy(declaredAt = DeclarationSiteRef("ServiceRole.kt", 73))) },
    )

    /** A `ServiceRole.kt` with enough lines for the sites the rows open. */
    private fun roleFile(name: String = "ServiceRole.kt") = myFixture.addFileToProject(name, (1..100).joinToString("\n") { "// line $it" }).virtualFile

    /** Opens HealthService.kt under [root]; the one ◆'s menu. With [resolveSites] the definition finds the lines in `ServiceRole.kt`. */
    private fun openMenu(resolveSites: Boolean = true): GutterMenu {
        if (resolveSites) {
            val file = roleFile()
            definitions.resolveWith(GutterFixtures.SERVICE_RULE.declaredAt!!, ResolvedSite.Found(file, 80))
            definitions.resolveWith(DeclarationSiteRef("ServiceRole.kt", 1), ResolvedSite.Found(file, 1))
        }
        ready(fileName, fileRoles(GutterFixtures.SERVICE_PATH, service, projectRoot = root), explained())
        drawn(fileName, GutterFixtures.HEALTH_SERVICE_KT)
        return menus().single()
    }

    fun `test メニューの行は 許可元 Role 兄弟 次のファイル 検査 更新 の順で 右に宣言元と親ディレクトリが付く`() {
        assertEquals(
            listOf(
                "◆ Allowed by class HealthService | ServiceRole.kt:80",
                "◇ Role domain.Service | ServiceRole.kt:1",
                "== Other files of domain.Service (1)",
                "LegacyStatusService.kt | src/main/kotlin/com/example/service",
                "-----",
                "+ New domain.Service from template…",
                "(disabled) ▶ Run the check (:arch:test)",
                "(disabled) ⟳ Refresh the definition",
            ),
            openMenu().shown(),
        )
    }

    fun `test 定義の行が見つかる宣言元は 押せて 見つからない行は 理由を言って押せない`() {
        definitions.resolveWith(GutterFixtures.SERVICE_RULE.declaredAt!!, ResolvedSite.Found(roleFile(), 80))
        val menu = openMenu(resolveSites = false)
        assertEquals(
            listOf(
                "◆ Allowed by class HealthService | ServiceRole.kt:80",
                "(disabled) ◇ Role domain.Service | (declaration not found: not scripted in the fake: DeclarationSiteRef(fileName=ServiceRole.kt, line=1))",
            ),
            menu.shown().take(2),
        )
    }

    fun `test 許可元の行を選ぶと 許可リストの行が開く`() {
        val roleFile = roleFile()
        definitions.resolveWith(GutterFixtures.SERVICE_RULE.declaredAt!!, ResolvedSite.Found(roleFile, 80))
        val menu = openMenu(resolveSites = false)

        choose(menu.row("◆ Allowed by"))

        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        assertEquals(roleFile, editor.virtualFile)
        assertEquals(79, editor.caretModel.logicalPosition.line)
        assertEquals(emptyList<String>(), balloons)
    }

    fun `test 見つからない宣言元の行を押しても何も起きず 開いているファイルは変わらない`() {
        val menu = openMenu(resolveSites = false)
        val before = FileEditorManager.getInstance(project).selectedFiles.toList()

        choose(menu.row("◆ Allowed by"))

        assertEquals(before, FileEditorManager.getInstance(project).selectedFiles.toList())
    }

    fun `test 同じ Role の他のファイルを選ぶと開き 無ければ見つからないと知らせる`() {
        val onDisk = root.resolve(legacy)
        Files.createDirectories(onDisk.parent)
        Files.writeString(onDisk, "class LegacyStatusService\n")
        val menu = openMenu()

        choose(menu.row("LegacyStatusService.kt"))

        assertEquals(onDisk, java.nio.file.Paths.get(FileEditorManager.getInstance(project).selectedFiles.single().path))
        assertEquals(emptyList<String>(), balloons)

        Files.delete(onDisk)
        choose(menu.row("LegacyStatusService.kt"))
        assertEquals(listOf(KatachiBundle.message("gutter.notFound", "LegacyStatusService.kt")), balloons)
    }

    fun `test 次のファイルを選ぶと 兄弟を起点にしたダイアログを 決まる capture の初期値つきで開く`() {
        seedPort.seeds = mapOf(templateId to mapOf("feature" to "home"))
        val menu = openMenu()

        choose(menu.row("+ New domain.Service from template…"))

        val file = root.resolve(GutterFixtures.SERVICE_PATH)
        assertEquals(listOf(GenerateDialogRequest(EntryOrigin.SiblingOf(file), templateId, mapOf("feature" to "home"))), effects.dialogs)
        assertEquals(listOf(EntryOrigin.SiblingOf(file) to templateId), seedPort.asked)
    }

    fun `test 検査と更新は 土台のアクションを このファイルと project つきで呼ぶ`() {
        val runCheck = RecordingAction("run")
        val refresh = RecordingAction("refresh")
        actions[GutterMenu.RUN_CHECK] = runCheck
        actions[GutterMenu.REFRESH] = refresh
        val menu = openMenu()

        choose(menu.row("▶ Run the check"))
        choose(menu.row("⟳ Refresh the definition"))

        assertEquals(1, runCheck.events.size)
        assertEquals(1, refresh.events.size)
        // The foundation's RunCheck finds its module from the file in the data context.
        assertEquals(myFixture.file.virtualFile, runCheck.events.single().getData(CommonDataKeys.VIRTUAL_FILE))
        assertEquals(project, runCheck.events.single().project)
    }

    fun `test 土台のアクションが無ければ 検査も更新も押せず 選んでも何も呼ばない`() {
        val menu = openMenu()
        val rows = listOf(menu.row("▶ Run the check"), menu.row("⟳ Refresh the definition"))
        assertEquals(listOf(false, false), rows.map(::isEnabled))
        rows.forEach(::choose)
        assertEquals(emptyList<String>(), balloons)
    }

    fun `test 検査が走っている間は 検査の行が検査中になり 終わると戻る`() {
        val runCheck = RecordingAction("run") { violations.runCheck(module(":arch").id) }
        actions[GutterMenu.RUN_CHECK] = runCheck
        val menu = openMenu()
        assertTrue(isEnabled(menu.row("▶ Run the check")))

        choose(menu.row("▶ Run the check"))
        assertEquals(listOf("(disabled) ▶ Checking…"), menu.shown().filter { it.contains("Checking") })
        choose(menu.row("▶ Checking"))
        assertEquals(1, runCheck.events.size)

        violations.finishCheck(module(":arch").id)
        assertEquals(listOf("▶ Run the check (:arch:test)"), menu.shown().filter { it.contains("Run the check") })
    }

    fun `test 定義が古いか 未保存の編集があれば 更新が先頭に来る`() {
        actions[GutterMenu.REFRESH] = RecordingAction("refresh")
        openMenu()
        assertEquals("⟳ Refresh the definition", menus().single().shown().last())

        typeAtStart("// a note\n")
        redrawn()
        assertEquals("⟳ Refresh the definition", menus().single().shown().first())
        assertEquals(1, menus().single().shown().count { it == "⟳ Refresh the definition" })

        saveAll()
        redrawn()
        assertEquals("saved edits are in a file newer than the definition: still first", "⟳ Refresh the definition", menus().single().shown().first())

        refreshedSnapshot()
        ready(fileName, fileRoles(GutterFixtures.SERVICE_PATH, service, projectRoot = root), explained())
        redrawn()
        assertEquals("⟳ Refresh the definition", menus().single().shown().last())
    }

    fun `test 定義が読み直されると 描き直した ◆ のメニューは新しい答えになる`() {
        openMenu()
        val other = roleModel("api.Handler", layoutEntry(GutterFixtures.SERVICE_PATH))
        definitions.resolveWith(DeclarationSiteRef("HandlerRole.kt", 1), ResolvedSite.Found(roleFile("HandlerRole.kt"), 1))
        ready(fileName, fileRoles(GutterFixtures.SERVICE_PATH, other, projectRoot = root))
        redrawn()
        val shown = menus().single().shown()
        assertEquals("◇ Role api.Handler | HandlerRole.kt:1", shown.first())
        assertEquals(false, shown.any { it.startsWith("+ New") })
    }

    fun `test dumb mode でも メニューを組めて 更新は選べる`() {
        val refresh = RecordingAction("refresh")
        actions[GutterMenu.REFRESH] = refresh
        ready(fileName, fileRoles(GutterFixtures.SERVICE_PATH, service, projectRoot = root), explained())
        myFixture.configureByText(fileName, GutterFixtures.HEALTH_SERVICE_KT)
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val handler = collectedInfos(myFixture.file).single().navigationHandler as GutterMenu
            assertTrue(handler.shown().first().contains("Allowed by class HealthService"))
            choose(handler.row("⟳ Refresh the definition"))
        }
        assertEquals(1, refresh.events.size)
    }

    fun `test 宣言が 200 個のファイルでも ◆ ごとにメニューを持ち 同じ宣言元は 1 回だけ探す`() {
        val text = (1..200).joinToString("\n") { "class C$it" }
        val declarations = (1..200).map { declarationModel(SourceDeclarationKind.Class, "C$it", it, rules = listOf(GutterFixtures.SERVICE_RULE)) }
        ready(fileName, fileRoles(GutterFixtures.SERVICE_PATH, service, projectRoot = root), listOf(roleFileDeclarations(service, fileDeclarations(GutterFixtures.SERVICE_PATH, *declarations.toTypedArray()))))
        myFixture.configureByText(fileName, text)
        val infos = collectedInfos(myFixture.file)
        assertEquals(200, infos.size)
        assertTrue(infos.all { it.navigationHandler is GutterMenu })
        assertEquals(setOf(GutterFixtures.SERVICE_RULE.declaredAt, DeclarationSiteRef("ServiceRole.kt", 1)), definitions.resolved.toSet())
        assertEquals("each site is looked up once per call of the daemon", 2, definitions.resolved.size)
    }

    fun `test ポップアップは見出しに宣言と Role を出し 行を速度検索の一覧に並べる`() {
        val menu = openMenu()
        val popup = menu.createPopup(com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT)
        try {
            assertEquals("HealthService — domain.Service", popup.listStep.title)
            assertEquals(menu.rows().size, popup.listStep.values.size)
            assertTrue(popup.listStep.hasSubstep(popup.listStep.values.first()).not())
        } finally {
            popup.cancel()
        }
    }

    fun `test テンプレートが 2 つ以上なら入れ子のメニューで 選ぶと選んだテンプレートでダイアログを開く`() {
        val two = base.withTemplates(templateRef(base, "service"), templateRef(base, "fake", title = "Fake service"))
        ready(fileName, fileRoles(GutterFixtures.SERVICE_PATH, two, projectRoot = root), listOf(roleFileDeclarations(two, fileDeclarations(GutterFixtures.SERVICE_PATH))))
        drawn(fileName, GutterFixtures.HEALTH_SERVICE_KT)
        val menu = menus().single()
        val submenu = menu.actionGroup(com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT).getChildren(null).filterIsInstance<GutterMenu.SubMenu>().single()
        assertTrue("a sub menu is hidden while indexing unless it is dumb aware", submenu.isDumbAware)
        assertTrue(submenu.isPopup)

        choose(menu.row("Fake service"))

        assertEquals(listOf(TemplateId(module(":arch").id, "domain.Service.fake")), effects.dialogs.map { it.initialTemplate })
    }

    fun `test ファイル名や規則や題の _ と & は ニーモニックにされず 行にそのまま出る`() {
        val oddRole = roleModel(
            "domain.Service",
            layoutEntry(GutterFixtures.SERVICE_PATH, matchedFiles = listOf(GutterFixtures.SERVICE_PATH, "res/layout/activity_main.xml", "res/Tom & Jerry.kt")),
        )
        val odd = oddRole.withTemplates(templateRef(oddRole, "fake", title = "Fake_service & co"), templateRef(oddRole, "real", title = "Real_service"))
        val rule = RuleRef("fun test_* & more", DeclarationSiteRef("ServiceRole.kt", 80))
        val file = roleFile()
        definitions.resolveWith(rule.declaredAt!!, ResolvedSite.Found(file, 80))
        definitions.resolveWith(DeclarationSiteRef("ServiceRole.kt", 1), ResolvedSite.Found(file, 1))
        val declared = declarationModel(SourceDeclarationKind.Class, "HealthService", 7, rules = listOf(rule))
        ready(fileName, fileRoles(GutterFixtures.SERVICE_PATH, odd, projectRoot = root), listOf(roleFileDeclarations(odd, fileDeclarations(GutterFixtures.SERVICE_PATH, declared))))
        drawn(fileName, GutterFixtures.HEALTH_SERVICE_KT)
        val menu = menus().single()

        val shown = menu.shown()
        assertTrue(shown.toString(), shown.contains("◆ Allowed by fun test_* & more | ServiceRole.kt:80"))
        assertTrue(shown.toString(), shown.contains("activity_main.xml | res/layout"))
        assertTrue(shown.toString(), shown.contains("Tom & Jerry.kt | res"))
        assertTrue(shown.toString(), shown.contains("  Fake_service & co"))
        assertTrue(shown.toString(), shown.contains("  Real_service"))
        // What the popup itself puts on screen: the list item reads the presentation, mnemonic or not.
        val popup = menu.createPopup(com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT)
        try {
            val texts = popup.listStep.values.map { (it as PopupFactoryImpl.ActionItem).text }
            assertTrue(texts.toString(), texts.containsAll(listOf("◆ Allowed by fun test_* & more", "activity_main.xml", "Tom & Jerry.kt", "+ New domain.Service from template…")))
        } finally {
            popup.cancel()
        }
    }

    fun `test 無効になっている土台のアクションを選んだら 何も起きなかったと知らせ dumb mode では知らせない`() {
        val refresh = RecordingAction("refresh")
        // The foundation's action is disabled through its template presentation, which the platform asks nobody to touch outside the action's own constructor.
        LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?) = emptySet<Action>()
        }) { refresh.templatePresentation.isEnabled = false }
        actions[GutterMenu.REFRESH] = refresh
        val menu = openMenu()

        choose(menu.row("⟳ Refresh the definition"))
        assertEquals(emptyList<com.intellij.openapi.actionSystem.AnActionEvent>(), refresh.events)
        assertEquals(listOf(KatachiBundle.message("gutter.actionIgnored", "refresh")), balloons)

        balloons.clear()
        DumbModeTestUtils.runInDumbModeSynchronously(project) { choose(menu.row("⟳ Refresh the definition")) }
        assertEquals("the platform says why itself while indexing", emptyList<String>(), balloons)
    }
}
