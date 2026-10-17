package me.tbsten.katachi.intellij.ide.authoring.gutter

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.gutter.GutterMenu
import me.tbsten.katachi.intellij.ide.gutter.RecordingAction
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrorModel
import me.tbsten.katachi.intellij.model.DefinitionErrorPhase
import me.tbsten.katachi.intellij.model.DefinitionErrorsEvent
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.declaredAt
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.module
import java.nio.file.Files
import java.nio.file.Paths

/** F-11 of ide-authoring: the ◆ of a role and of a `.template { }` in a definition file, over the real PSI and daemon with the foundation's fake. */
internal class DefinitionGutterTest : DefinitionGutterTestBase() {
    private val templateId = TemplateId(arch, "domain.Service.service")

    private fun openedMarks(): List<DrawnDefinitionMark> {
        writeFiles()
        openDefinition()
        readDefinition()
        return definitionMarks()
    }

    fun `test 役割の行と template の行にだけ ◆ が出て 役割の tooltip に件数がある`() {
        val marks = openedMarks()

        assertEquals(listOf(2, 4), marks.map { it.line })
        assertEquals(listOf("\"", "template"), marks.map { it.leaf })
        assertEquals(
            listOf("katachi: domain.Service — 3 files", "katachi: generate from domain.Service"),
            marks.map { it.tooltip },
        )
    }

    fun `test 役割 ◆ のメニューは 当たるファイル 生成 検査 読み直し の順で 検査と読み直しは土台のアクションが無ければ選べない`() {
        val menu = openedMarks().first().menu

        assertEquals(
            listOf(
                "HealthService.kt | core/service",
                "PingService.kt | core/service",
                "UserService.kt | core/service",
                "+ Generate from this template…",
                "-----",
                "(disabled) ▶ Run the check (:arch:test)",
                "(disabled) ⟳ Reload Definition",
            ),
            menu.shown(),
        )
    }

    fun `test 当たるファイルの行を選ぶと そのファイルが開き 無ければ見つからないと知らせる`() {
        val menu = openedMarks().first().menu

        menu.row("PingService.kt").choose()

        assertEquals(root.resolve("core/service/PingService.kt"), Paths.get(FileEditorManager.getInstance(project).selectedFiles.single().path))
        assertEquals(emptyList<String>(), balloons)

        Files.delete(root.resolve("core/service/UserService.kt"))
        menu.row("UserService.kt").choose()
        assertEquals(listOf(KatachiBundle.message("gutter.notFound", "UserService.kt")), balloons)
    }

    fun `test Generate を選ぶと 定義ファイルを起点に template を選んだ状態でダイアログを開く`() {
        val menu = openedMarks().first().menu

        menu.row("+ Generate from this template").choose()

        val definitionFile = Paths.get(myFixture.file.virtualFile.path)
        assertEquals(listOf(GenerateDialogRequest(EntryOrigin.Definition(definitionFile), templateId, emptyMap())), effects.dialogs)
        assertEquals(listOf(EntryOrigin.Definition(definitionFile) to templateId), seedPort.asked)
    }

    fun `test template の ◆ は Generate と ツールウィンドウで見る を持ち それぞれ選べる`() {
        val menu = openedMarks().last().menu

        assertEquals(listOf("+ Generate from this template…", "⊞ Show in the tool window"), menu.shown())
        menu.row("⊞ Show in the tool window").choose()
        menu.row("+ Generate from this template").choose()

        assertEquals(listOf(templateId), effects.revealed)
        assertEquals(listOf(templateId), effects.dialogs.map { it.initialTemplate })
    }

    fun `test 検査と読み直しは 土台のアクションを この定義ファイルと project つきで呼ぶ`() {
        val runCheck = RecordingAction("run")
        val refresh = RecordingAction("refresh")
        actions[GutterMenu.RUN_CHECK] = runCheck
        actions[GutterMenu.REFRESH] = refresh
        val menu = openedMarks().first().menu

        menu.row("▶ Run the check").choose()
        menu.row("⟳ Reload Definition").choose()

        assertEquals(1, runCheck.events.size)
        assertEquals(1, refresh.events.size)
        assertEquals(myFixture.file.virtualFile, runCheck.events.single().getData(CommonDataKeys.VIRTUAL_FILE))
        assertEquals(project, runCheck.events.single().project)
    }

    fun `test 定義の行が編集でずれても 保存すれば ◆ は動いた行に付き 件数も同じで stale にならない`() {
        openedMarks()

        typeAtStart("// a\n// b\n// c\n")
        saveAll()
        val moved = definitionMarks()

        assertEquals(listOf(5, 7), moved.map { it.line })
        assertEquals("katachi: domain.Service — 3 files", moved.first().tooltip)
    }

    fun `test 定義ファイルに保存していない編集がある間は 前の件数を stale つきで出し 保存すれば外れる`() {
        openedMarks()

        typeAtStart("// a\n")

        assertEquals("katachi: domain.Service — 3 files (stale)", definitionMarks().first().tooltip)
        saveAll()
        assertEquals("katachi: domain.Service — 3 files", definitionMarks().first().tooltip)
    }

    fun `test 定義の JSON に projectRoot が無いときは 土台と同じに 連携した Gradle のルートを基準にファイルを開く`() {
        writeFiles()
        val file = openDefinition()
        val rooted = module(":arch", root = root)
        definitions.holdDefinitionFile(file.virtualFile, rooted.id)
        snapshot = definitionSnapshot(definitionModel(serviceRole(), projectRoot = root).copy(projectRoot = null), rooted)
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        val menu = definitionMarks().first().menu

        menu.row("PingService.kt").choose()

        assertEquals(root.resolve("core/service/PingService.kt"), Paths.get(FileEditorManager.getInstance(project).selectedFiles.single().path))
        assertEquals(emptyList<String>(), balloons)
    }

    fun `test 名前を変えた役割には 次に読み直すまで ◆ が付かない`() {
        openedMarks()

        renameRole("Svc")

        assertEquals(listOf(4), definitionMarks().map { it.line })
    }

    fun `test 定義モジュールの外のファイルには 同じ形が書かれていても ◆ が出ない`() {
        writeFiles()
        open(ROLE_FILE, DEFINITION_SOURCE)
        readDefinition()

        assertEquals(emptyList<DrawnDefinitionMark>(), definitionMarks())
    }

    fun `test 宣言の場所が同じ名前のファイルが2つあって決まらないときは ◆ が出ない`() {
        writeFiles()
        openDefinition()
        definitions.resolveWith(DeclarationSiteRef(ROLE_FILE, 2), ResolvedSite.Unresolved("2 files named ServiceRole.kt"))
        readDefinition()

        assertEquals(listOf(4), definitionMarks().map { it.line })
    }

    fun `test 200 を超えるファイルは 残りの数を言う行で切られる`() {
        val many = (1..250).map { "core/service/S$it.kt" }
        openDefinition()
        readDefinition(serviceRole(many, templated = false))

        val menu = definitionMarks().single().menu

        assertEquals("katachi: domain.Service — 250 files", definitionMarks().single().tooltip)
        assertEquals(listOf("(disabled) … and 50 more (open the tool window)"), menu.shown().filter { it.contains("…") })
    }

    fun `test 同じ名前の役割が2つあるときは それぞれの ◆ が 自分の行に近い方の役割の件数を言う`() {
        val near = roleModel("a.Model", layoutEntry("a/*Model.kt", matchedFiles = listOf("a/One.kt")).declaredAt(ROLE_FILE, 3)).declaredAt(ROLE_FILE, 2)
        val far = roleModel("b.Model", layoutEntry("b/*Model.kt", matchedFiles = listOf("b/One.kt", "b/Two.kt")).declaredAt(ROLE_FILE, 9)).declaredAt(ROLE_FILE, 8)
        val source = "fun definitions() {\n    \"Model\".role {\n        \"*Model\".ktFile()\n    }\n\n\n\n    \"Model\".role {\n        \"*Model\".ktFile()\n    }\n}\n"
        openDefinition(source)
        snapshot = definitionSnapshot(definitionModel(near, far, projectRoot = root), module(":arch"))
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))

        val marks = definitionMarks()

        assertEquals(listOf(2 to "katachi: a.Model — 1 file", 8 to "katachi: b.Model — 2 files"), marks.map { it.line to it.tooltip })
    }

    // F-12: the states, as the real daemon draws them.
    fun `test 状態の列 読み込み前は出ず 答えがあれば出し続け 古い答えには stale が付き 古すぎる katachi では出ない`() {
        writeFiles()
        openDefinition()
        val fresh = DefinitionState.Ready(listOf(definitionSnapshotOf()), Freshness.Fresh)
        val stale = DefinitionState.Ready(fresh.definitions, Freshness.Stale(tick(), StaleReason.DefinitionChanged))
        val steps = listOf<Pair<DefinitionState, String?>>(
            DefinitionState.NotLoaded to null,
            fresh to "katachi: domain.Service — 3 files",
            DefinitionState.Loading(fresh) to "katachi: domain.Service — 3 files",
            stale to "katachi: domain.Service — 3 files (stale)",
            DefinitionState.Unavailable(DefinitionUnavailableReason.LoadFailed, fresh) to "katachi: domain.Service — 3 files (stale)",
            fresh to "katachi: domain.Service — 3 files",
            DefinitionState.Unavailable(DefinitionUnavailableReason.KatachiTooOld, fresh) to null,
        )

        definitions.play(steps.map { it.first }) { state ->
            val expected = steps.first { it.first === state }.second
            assertEquals("$state", listOfNotNull(expected), definitionMarks().filter { it.line == 2 }.map { it.tooltip })
        }
        assertTrue("the first look asked the definition to load", definitions.ensureLoadedCalls >= 1)
    }

    fun `test katachi が定義に誤りを見つけている間は 前の答えの件数を stale つきで出す`() {
        writeFiles()
        openDefinition()
        readDefinition()
        assertEquals("katachi: domain.Service — 3 files", definitionMarks().first().tooltip)

        definitions.emitErrors(DefinitionErrorsEvent.LoadRejected(listOf(rejection()), tick()))

        assertEquals("katachi: domain.Service — 3 files (stale)", definitionMarks().first().tooltip)
    }

    /** Types another name over the role's `"Service"`, as an edit that the definition has not been read again for. */
    private fun renameRole(name: String) = WriteCommandAction.runWriteCommandAction(project) {
        val document = myFixture.editor.document
        val start = document.text.indexOf("Service\".role")
        document.replaceString(start, start + "Service".length, name)
    }

    private fun definitionSnapshotOf() = definitionSnapshot(
        definitionModel(serviceRole(), projectRoot = root),
        module(":arch"),
    ).also { snapshot = it }

    private fun rejection() = DefinitionErrorModel(
        arch, DefinitionErrorPhase.Processor, "internalDefinitionJson",
        "me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException", "boom", null,
    )
}
