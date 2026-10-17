package me.tbsten.katachi.intellij.ide.authoring.codevision

import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.ide.authoring.gutter.DefinitionGutterTestBase
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.presentation.authoring.EntryFilesPopupModel
import me.tbsten.katachi.intellij.testing.declaredAt
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import java.nio.file.Files
import java.nio.file.Path

/**
 * F-13 of ide-authoring: the Code Vision at the end of a layout line. `computeForEditor` is called directly (the platform draws the
 * inlays from what it returns, and a unit test has no inlay renderer), and a press is `handleClick` with the popup captured instead
 * of shown.
 */
internal class EntryFilesCodeVisionTest : DefinitionGutterTestBase() {
    private val popups = mutableListOf<EntryFilesPopupModel>()
    private val opened = mutableListOf<Path>()

    private fun provider() = EntryFilesCodeVisionProvider({ ports() }, background = { it() }, show = { _, _, model, open ->
        popups += model
        model.rows.firstNotNullOfOrNull { it.target }?.let { open(it); opened.add(it) }
    })

    private fun lenses(role: RoleModel = serviceRole(), freshness: Freshness = Freshness.Fresh): List<Pair<Int, String>> {
        writeFiles()
        val file = openDefinition()
        readDefinition(role, freshness)
        val document = myFixture.editor.document
        return provider().computeForEditor(myFixture.editor, file).map { (range, entry) -> document.getLineNumber(range.startOffset) + 1 to (entry as TextCodeVisionEntry).text }
    }

    fun `test layout の行の末尾に 当たるファイルの数が出て 役割と template の行には出ない`() {
        assertEquals(listOf(3 to "3 files"), lenses())
    }

    fun `test 1 件なら 1 file 0 件なら no files 必須で 0 件なら missing`() {
        val role = { files: List<String>, required: Boolean ->
            roleModel("domain.Service", layoutEntry("core/service/*Service.kt", required = required, matchedFiles = files).declaredAt(ROLE_FILE, 3)).declaredAt(ROLE_FILE, 2)
        }
        assertEquals(listOf(3 to "1 file"), lenses(role(listOf("core/service/HealthService.kt"), false)))
        assertEquals(listOf(3 to "no files"), lenses(role(emptyList(), false)))
        assertEquals(listOf(3 to "missing (required)"), lenses(role(emptyList(), true)))
    }

    fun `test 検査がまだで 件数が分からない行は 疑問符で言う`() {
        val role = roleModel("domain.Service", layoutEntry("core/service/*Service.kt").copy(matchedCount = null).declaredAt(ROLE_FILE, 3)).declaredAt(ROLE_FILE, 2)

        assertEquals(listOf(3 to "? files"), lenses(role))
    }

    fun `test JSON が一覧を切った行は 並んだ数に プラスを付ける`() {
        val role = roleModel(
            "domain.Service",
            layoutEntry("core/service/*Service.kt", matchedFiles = SERVICE_FILES).copy(matchedCount = 900, truncated = true).declaredAt(ROLE_FILE, 3),
        ).declaredAt(ROLE_FILE, 2)

        assertEquals(listOf(3 to "3+ files"), lenses(role))
    }

    fun `test 答えが古いときは 件数の末尾に stale が付く`() {
        val stale = Freshness.Stale(java.time.Instant.EPOCH, StaleReason.DefinitionChanged)
        assertEquals(listOf(3 to "3 files (stale)"), lenses(freshness = stale))
    }

    fun `test wildcard の module key で 1 つの宣言が module ごとの entry になったら 合計を言い modules の数を付ける`() {
        val role = roleModel(
            "domain.Service",
            layoutEntry("a/core/*Service.kt", modulePath = ":a", matchedFiles = listOf("a/core/AService.kt", "a/core/BService.kt")).declaredAt(ROLE_FILE, 3),
            layoutEntry("b/core/*Service.kt", modulePath = ":b", matchedFiles = listOf("b/core/CService.kt")).declaredAt(ROLE_FILE, 3),
        ).declaredAt(ROLE_FILE, 2)

        assertEquals(listOf(3 to "3 files (2 modules)"), lenses(role))
    }

    fun `test 定義ファイルでないファイルと 宣言の無い行には 何も出ない`() {
        writeFiles()
        val other = open("Other.kt", "fun other() {\n    \"*Service\".ktFile()\n}\n")
        definitions.emit(DefinitionState.Ready(listOf(definitionSnapshot(definitionModel(serviceRole(), projectRoot = root), module(":arch"))), Freshness.Fresh))

        assertEquals(emptyList<Any>(), provider().computeForEditor(myFixture.editor, other))
    }

    fun `test lens の id と group と anchor は 設定ページの group katachi に属し 行の右に出る`() {
        val provider = provider()

        assertEquals("katachi.entryFiles", provider.id)
        assertEquals("katachi", provider.groupId)
        assertEquals(com.intellij.codeInsight.codeVision.CodeVisionAnchorKind.Right, provider.defaultAnchor)
        assertEquals("katachi", KatachiCodeVisionGroup().groupId)
    }

    fun `test Code Vision は 索引の最中には出さない DumbAware ではない`() {
        assertFalse(DumbService.isDumbAware(provider()))
        // The ◆ of the same file still says the count (DefinitionDumbModeTest); here only that the provider is not asked while indexing.
        assertTrue(DumbModeTestUtils.computeInDumbModeSynchronously(project) { DumbService.getInstance(project).isDumb })
    }

    fun `test 押すと 当たるファイルが popup に並び ファイルを選ぶとそのファイルが開く`() {
        writeFiles()
        val file = openDefinition()
        readDefinition()
        val provider = provider()
        val (range, entry) = provider.computeForEditor(myFixture.editor, file).single()

        provider.handleClick(myFixture.editor, range, entry)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val popup = popups.single()
        assertEquals("core/service/*Service.kt", popup.title)
        assertEquals(listOf("HealthService.kt", "PingService.kt", "UserService.kt"), popup.rows.map { it.text })
        assertEquals(listOf(root.resolve("core/service/HealthService.kt")), opened)
        assertEquals("HealthService.kt", FileEditorManager.getInstance(project).selectedFiles.single().name)
    }

    fun `test 0 件の行を押すと glob と そのディレクトリに実際にあるファイルが並ぶ`() {
        Files.createDirectories(root.resolve("core/service"))
        Files.writeString(root.resolve("core/service/Other.kt"), "class Other\n")
        Files.writeString(root.resolve("core/service/Another.kt"), "class Another\n")
        Files.createDirectories(root.resolve("core/service/nested"))
        val role = roleModel("domain.Service", layoutEntry("core/service/*Servise.kt").declaredAt(ROLE_FILE, 3)).declaredAt(ROLE_FILE, 2)
        // The glob has a typo, and the source says the same: the line is tied to its declaration by that name.
        val file = openDefinition(DEFINITION_SOURCE.replace("*Service", "*Servise"))
        readDefinition(role)
        val provider = provider()
        val (range, entry) = provider.computeForEditor(myFixture.editor, file).single()

        provider.handleClick(myFixture.editor, range, entry)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(
            listOf("— No file matches core/service/*Servise.kt", "— Files in core/service:", "Another.kt", "Other.kt"),
            popups.single().rows.map { it.text },
        )
    }

    fun `test 押したのが この provider の lens でなければ 何も起きない`() {
        val file = openDefinition()
        readDefinition()
        val provider = provider()
        val (range, _) = provider.computeForEditor(myFixture.editor, file).single()

        provider.handleClick(myFixture.editor, range, TextCodeVisionEntry("x", "other", null, "x", "x", emptyList()))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertTrue(popups.isEmpty())
    }
}
