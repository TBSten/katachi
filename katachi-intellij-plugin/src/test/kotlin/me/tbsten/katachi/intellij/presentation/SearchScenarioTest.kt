package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search as the user types it: narrowing, emptying, and searching around checks, reloads and
 * generations (spec 02 "検索", spec 04 "検索で絞ったとき"). Linked fields across two generations too.
 */
class SearchScenarioTest {
    private val all = listOf("data/Repository", "domain/UseCase", "ui/Screen", "misc/Broken", "misc/Future", "misc/NoArgs", "misc/Label")

    private fun ScenarioHarness.shownRoles() = rowIds().map { it.roleName }

    /** Types [text] one character at a time, as the search field reports it. */
    private fun ScenarioHarness.type(text: String, from: String = state.searchQuery) {
        text.indices.forEach { dispatch(KatachiIntent.Search(from + text.substring(0, it + 1))) }
    }

    @Test
    fun `打って絞り込み空に戻すと全件に戻り1文字ずつ消しても途中で全件が消えない`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        assertEquals(all, s.shownRoles())
        s.type("repo")
        assertEquals(listOf("data/Repository"), s.shownRoles())
        assertEquals("repo", s.ui().search.query)

        s.dispatch(KatachiIntent.Search(""))
        assertEquals(all, s.shownRoles())
        assertNull(s.ui().emptySearch)

        s.type("repo")
        for (query in listOf("rep", "re", "r", "")) {
            s.dispatch(KatachiIntent.Search(query))
            assertTrue("\"$query\" hid every row", s.shownRoles().isNotEmpty())
            assertNull(s.ui().emptySearch)
        }
        assertEquals(all, s.shownRoles())
    }

    @Test
    fun `一致0件のあと空に戻すかクリアを押すと全件に戻る`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.type("zzz")
        assertTrue(s.shownRoles().isEmpty())
        assertEquals("「zzz」に一致するテンプレートはありません", s.ui().emptySearch?.title)
        val clear = s.ui().emptySearch?.actions?.single()?.intent
        assertEquals(KatachiIntent.Search(""), clear)

        s.dispatch(clear!!)
        assertEquals(all, s.shownRoles())
        assertNull(s.ui().emptySearch)

        s.type("zzz")
        s.dispatch(KatachiIntent.Search("zz"), KatachiIntent.Search("z"), KatachiIntent.Search(""))
        assertEquals(all, s.shownRoles())
    }

    @Test
    fun `空白だけは絞らず前後の空白と大文字小文字と日本語のタイトルや説明でも一致する`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.dispatch(KatachiIntent.Search("   "))
        assertEquals(all, s.shownRoles())
        assertNull(s.ui().emptySearch)
        assertEquals("   ", s.ui().search.query)

        s.dispatch(KatachiIntent.Search("  REPOSITORY "))
        assertEquals(listOf("data/Repository"), s.shownRoles())
        s.dispatch(KatachiIntent.Search("リポジトリ"))
        assertEquals(listOf("data/Repository"), s.shownRoles())
        s.dispatch(KatachiIntent.Search("画面"))
        assertEquals(listOf("ui/Screen"), s.shownRoles())
        s.dispatch(KatachiIntent.Search("withimpl"))
        assertEquals(listOf("data/Repository"), s.shownRoles())
        s.dispatch(KatachiIntent.Search("ユースケース"))
        assertEquals(listOf("domain/UseCase"), s.shownRoles())
    }

    @Test
    fun `検索中にチェックして入力してから空に戻してもチェックと入力が残り検索外の行も生成に入る`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.type("repo")
        s.check(s.repository)
        s.input(s.repository, "name", "User")
        s.dispatch(KatachiIntent.Search("usecase"))
        assertEquals(listOf("data/Repository", "domain/UseCase"), s.shownRoles())
        assertEquals("検索外・選択中", s.row(s.repository).note)
        assertNull(s.row(s.repository).body)
        assertNull(s.formFooter().reason)

        s.dispatch(KatachiIntent.Search(""))
        assertEquals(all, s.shownRoles())
        assertEquals("User", s.textField(s.repository, "name").value)
        assertNull(s.row(s.repository).note)
        assertEquals(listOf(s.repository), s.state.form.selected)
    }

    @Test
    fun `検索中に再読み込みしても検索は残り空に戻すと新しい一覧の全件が出る`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.type("misc")
        s.reload(ContractFixtures.json("arch-a").replace("\"misc/Label\"", "\"misc/Renamed\""))
        assertEquals("misc", s.ui().search.query)
        assertEquals(listOf("misc/Broken", "misc/Future", "misc/NoArgs", "misc/Renamed"), s.shownRoles())

        s.dispatch(KatachiIntent.Search(""))
        assertEquals(all.dropLast(1) + "misc/Renamed", s.shownRoles())
    }

    @Test
    fun `検索中に生成すると結果の間は検索欄を触れず戻ってから空にすると全件に戻る`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.check(s.repository)
        s.input(s.repository, "name", "User")
        s.type("usecase", from = "")
        s.generate()
        assertEquals(false, s.ui().search.enabled)
        assertEquals(listOf("data/Repository", "domain/UseCase"), s.shownRoles())
        assertEquals(RowLeadUi.Status(RowStatus.Done), s.row(s.repository).lead)

        s.dispatch(KatachiIntent.ContinueGenerating)
        assertEquals(true, s.ui().search.enabled)
        s.dispatch(KatachiIntent.Search(""))
        assertEquals(all, s.shownRoles())
        assertEquals(RowLeadUi.Check(checked = true, enabled = true), s.row(s.repository).lead)
    }

    @Test
    fun `検索で絞ったまま結果の画面を離れた直後の一覧は検索を保ち離れ方ごとにチェックの残りを反映する`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.check(s.repository)
        s.input(s.repository, "name", "User")
        s.check(s.useCase)
        s.type("repo", from = "")
        assertEquals(listOf("data/Repository", "domain/UseCase"), s.shownRoles())
        s.generate()

        // "Generate more": both stay checked; the one outside the search keeps its note and no form.
        s.dispatch(KatachiIntent.ContinueGenerating)
        assertEquals("repo", s.ui().search.query)
        assertEquals(true, s.ui().search.enabled)
        assertEquals(listOf("data/Repository", "domain/UseCase"), s.shownRoles())
        assertTrue(s.row(s.repository).body is RowBodyUi.Form)
        assertEquals("検索外・選択中", s.row(s.useCase).note)
        assertNull(s.row(s.useCase).body)
        assertNull(s.ui().emptySearch)
        s.input(s.repository, "name", "Order")
        assertEquals("Order", s.inputOf(s.useCase, "name"))
        s.generate()

        // "Uncheck all": nothing is checked, so only what the search matches is left.
        s.dispatch(KatachiIntent.UncheckAll)
        assertEquals("repo", s.ui().search.query)
        assertEquals(listOf("data/Repository"), s.shownRoles())
        assertEquals(RowLeadUi.Check(checked = false, enabled = true), s.row(s.repository).lead)
        assertEquals(false, s.formFooter().generateEnabled)
    }

    @Test
    fun `検索で絞ったまま一致0件にして結果の画面を離れても検索外の選択中の行は出ている`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.check(s.repository)
        s.input(s.repository, "name", "User")
        s.type("zzz", from = "")
        assertEquals(listOf("data/Repository"), s.shownRoles())
        s.generate()

        s.dispatch(KatachiIntent.ContinueGenerating)
        assertEquals(listOf("data/Repository"), s.shownRoles())
        assertNull(s.ui().emptySearch)
        s.input(s.repository, "name", "Order")
        s.generate()
        s.dispatch(KatachiIntent.UncheckAll)
        assertTrue(s.shownRoles().isEmpty())
        assertEquals("「zzz」に一致するテンプレートはありません", s.ui().emptySearch?.title)
    }

    @Test
    fun `同名の欄を連動させたまま2回生成すると2回目も両方に同じ名前が入る`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.check(s.repository)
        s.check(s.useCase)
        s.input(s.repository, "name", "User")
        assertEquals(LinkUi.Linked, s.textField(s.useCase, "name").link)
        s.generate()
        assertEquals(listOf("User", "User"), s.katachi.runs.map { it["name"] })

        s.dispatch(KatachiIntent.ContinueGenerating)
        assertEquals("", s.textField(s.useCase, "name").value)
        s.input(s.useCase, "name", "Order")
        assertEquals("Order", s.textField(s.repository, "name").value)
        assertEquals(LinkUi.Linked, s.textField(s.repository, "name").link)
        s.generate()
        assertEquals(listOf("Order", "Order"), s.katachi.runs.drop(2).map { it["name"] })
    }

    @Test
    fun `連動を切ったまま2回生成すると2回目も切れたままでそれぞれの名前を送る`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.check(s.repository)
        s.check(s.useCase)
        s.input(s.repository, "name", "User")
        s.input(s.useCase, "name", "GetUser")
        assertTrue(s.textField(s.useCase, "name").link is LinkUi.Unlinked)
        s.generate()
        assertEquals(listOf("User", "GetUser"), s.katachi.runs.map { it["name"] })

        s.dispatch(KatachiIntent.ContinueGenerating)
        s.input(s.repository, "name", "Order")
        // Provisional: the link the user broke stays broken after "generate more".
        assertEquals("", s.textField(s.useCase, "name").value)
        assertTrue(s.textField(s.useCase, "name").link is LinkUi.Unlinked)
        s.input(s.useCase, "name", "GetOrder")
        s.generate()
        assertEquals(listOf("Order", "GetOrder"), s.katachi.runs.drop(2).map { it["name"] })

        s.dispatch(KatachiIntent.ContinueGenerating)
        val relink = s.textField(s.useCase, "name").link.let { it as LinkUi.Unlinked }.relink
        s.input(s.repository, "name", "Item")
        s.dispatch(relink)
        assertEquals("Item", s.textField(s.useCase, "name").value)
        assertEquals(LinkUi.Linked, s.textField(s.useCase, "name").link)
    }
}
