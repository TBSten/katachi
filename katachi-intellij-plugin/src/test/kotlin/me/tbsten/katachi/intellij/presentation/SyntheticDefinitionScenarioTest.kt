package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.cast
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tool window over `synthetic-*.json`, hand-written to the design draft section 6 shape
 * (`template`/`id`/`title`/`conflict`, `pattern`/`captures`/`parameters` on `files[]`) to cover
 * every shape a template can take: nested groups, a blocked role, a wildcard target, linked and
 * unlinked same-named fields, a 300-row list, and two definition modules.
 */
class SyntheticDefinitionScenarioTest {
    private fun ScenarioHarness.id(template: String) = TemplateId(arch.id, template)

    private fun ScenarioHarness.groupTitles() = ui().items.filterIsInstance<ListItemUi.GroupHeader>().map { it.title }

    private fun ScenarioHarness.fieldsOf(template: String) = form(id(template)).fields

    @Test
    fun `ルート直下の役割が先で入れ子のグループは親の直下に子が並び見出しはパス全体になる`() = runBlocking {
        val s = ScenarioHarness(this)
        s.loadJson = ContractFixtures.json("synthetic-structure")
        s.open()
        assertEquals(
            listOf("AllTypes", "Counter", "a.Shallow", "a.b.Middle", "a.b.c.Deep", "other.Broken", "other.Wildcard"),
            s.rowIds().map { it.template },
        )
        assertEquals(listOf("a", "a › b", "a › b › c", "other"), s.groupTitles())
        assertEquals(RowMarker.Blocked, s.row(s.id("a.b.Middle")).marker)
        assertEquals(RowMarker.Blocked, s.row(s.id("other.Broken")).marker)
        // Below a module capture, but katachi says where each module puts it: nothing to warn about.
        assertEquals(RowMarker.None, s.row(s.id("other.Wildcard")).marker)
        assertTrue(s.rowIds().all { s.row(it).trailing.isEmpty() })
    }

    @Test
    fun `全部の型の引数が型ごとの部品になり既定値は薄く出て必須の印が付く`() = runBlocking {
        val s = ScenarioHarness(this)
        s.loadJson = ContractFixtures.json("synthetic-structure")
        s.open()
        s.check(s.id("AllTypes"))
        val fields = s.fieldsOf("AllTypes").associateBy { it.id.parameterName }
        val name = fields.getValue("name").cast<FieldUi.Text>()
        assertTrue(name.isRequired)
        assertEquals(false, name.isMultiline)
        assertEquals("ラベル", fields.getValue("label").cast<FieldUi.Text>().placeholder)
        assertEquals(true, fields.getValue("enabled").cast<FieldUi.Bool>().checked)
        assertEquals(false, fields.getValue("verbose").cast<FieldUi.Bool>().checked)
        val count = fields.getValue("count").cast<FieldUi.Text>()
        assertTrue(count.isNumber && count.isRequired)
        assertEquals(null, count.isMultiline)
        assertEquals("20", fields.getValue("pageSize").cast<FieldUi.Text>().placeholder)
        val mode = fields.getValue("mode").cast<FieldUi.Choice>()
        assertEquals(listOf("Compact", "Expanded"), mode.options)
        assertEquals(-1, mode.selectedIndex)
        assertEquals(0, fields.getValue("fallbackMode").cast<FieldUi.Choice>().selectedIndex)
        // The row's name is now the template's own title (design draft section 6), so the reason
        // line names it in Japanese, not by the bare specifier.
        assertEquals("全部の型: name が未入力です", s.formFooter().reason)

        s.input(s.id("AllTypes"), "name", "User")
        s.input(s.id("AllTypes"), "count", "3")
        s.input(s.id("AllTypes"), "mode", "Expanded")
        s.generate()
        assertEquals(mapOf("template" to "AllTypes", "onExisting" to "fail", "name" to "User", "enabled" to "true", "count" to "3", "mode" to "Expanded"), s.lastArgs())
    }

    @Test
    fun `Booleanとenumの分岐で引数の欄が出入りし生成はそのとき出ている引数だけを送る`() = runBlocking {
        val s = ScenarioHarness(this)
        s.loadJson = ContractFixtures.json("synthetic-structure")
        s.open()
        val shallow = s.id("a.Shallow")
        s.check(shallow)
        fun slots() = s.fieldsOf("a.Shallow").map { if (it is FieldUi.Collapsed) "(${it.id.parameterName})" else it.id.parameterName }
        assertEquals(listOf("name", "withImpl", "implSuffix", "withTest", "(testName)", "style", "(decoration)"), slots())

        s.input(shallow, "withImpl", "false")
        s.input(shallow, "withTest", "true")
        s.input(shallow, "style", "Fancy")
        assertEquals(listOf("name", "withImpl", "(implSuffix)", "withTest", "testName", "style", "decoration"), slots())
        assertEquals("\${name}Test", s.textField(shallow, "testName").placeholder)
        s.input(shallow, "name", "Login")
        assertEquals("LoginTest", s.textField(shallow, "testName").placeholder)

        s.generate()
        assertEquals(setOf("template", "onExisting", "name", "withImpl", "withTest", "style"), s.lastArgs().keys)
    }

    @Test
    fun `同名同型の欄は連動し同名異型の欄は連動しない`() = runBlocking {
        val s = ScenarioHarness(this)
        s.loadJson = ContractFixtures.json("synthetic-structure")
        s.open()
        s.check(s.id("AllTypes"))
        s.check(s.id("a.b.c.Deep"))
        s.check(s.id("Counter"))
        s.input(s.id("AllTypes"), "name", "User")
        assertEquals("User", s.textField(s.id("a.b.c.Deep"), "name").value)
        assertEquals(LinkUi.Linked, s.textField(s.id("a.b.c.Deep"), "name").link)
        assertEquals("", s.textField(s.id("Counter"), "name").value)
        assertEquals(LinkUi.None, s.textField(s.id("Counter"), "name").link)
        assertEquals("UserItem", s.textField(s.id("a.b.c.Deep"), "itemName").placeholder)
    }

    @Test
    fun `モジュールのcaptureに今あるモジュールを入れると生成先が決まり生成できる`() = runBlocking {
        val s = ScenarioHarness(this)
        s.loadJson = ContractFixtures.json("synthetic-structure")
        s.open()
        val wildcard = s.id("other.Wildcard")
        s.check(wildcard)
        assertEquals("other.Wildcard: feature が未入力です", s.formFooter().reason)

        s.input(wildcard, "feature", "settings")
        assertEquals("生成先 feature/settings/src/main/kotlin/<name>Screen.kt", s.textField(wildcard, "feature").hint)
        s.input(wildcard, "name", "Home")
        assertEquals("生成先 feature/settings/src/main/kotlin/HomeScreen.kt", s.textField(wildcard, "feature").hint)
        assertTrue(s.formFooter().generateEnabled)

        s.generate()
        assertEquals(mapOf("template" to "other.Wildcard", "onExisting" to "fail", "feature" to "settings", "name" to "Home"), s.lastArgs())
    }

    @Test
    fun `モジュールのcaptureに無いモジュールを入れると生成できず今あるモジュールを示す`() = runBlocking {
        val s = ScenarioHarness(this)
        s.loadJson = ContractFixtures.json("synthetic-structure")
        s.open()
        val wildcard = s.id("other.Wildcard")
        s.check(wildcard)
        s.input(wildcard, "name", "Home")

        s.input(wildcard, "feature", "hoem")

        assertFalse(s.formFooter().generateEnabled)
        assertEquals("other.Wildcard: feature に当たるモジュールがありません", s.formFooter().reason)
        assertEquals("今あるモジュールから選んでください（home, settings）", s.textField(wildcard, "feature").error)
    }

    @Test
    fun `モジュールごとの生成先を書かない古いkatachiではモジュールのcaptureの下は入力しても生成できずその理由を出す`() = runBlocking {
        val s = ScenarioHarness(this)
        s.loadJson = ContractFixtures.json("synthetic-structure").withoutModulePlacements()
        s.open()
        s.check(s.id("other.Wildcard"))
        // "feature" is the module's own capture -- required like any other field, and filling it
        // does not resolve the path: the module capture itself is what keeps `path` null.
        s.input(s.id("other.Wildcard"), "feature", "home")
        s.input(s.id("other.Wildcard"), "name", "Home")
        assertFalse(s.formFooter().generateEnabled)
        // Neither the template nor its role sets a title, so it falls back to the full
        // specifier (`template`), not the bare role name.
        assertEquals("other.Wildcard: HomeScreen.kt の生成先が決まりません", s.formFooter().reason)
    }

    @Test
    fun `300件の一覧を入れ子の見出しつきで出し検索で絞ってから生成できる`() = runBlocking {
        val s = ScenarioHarness(this)
        s.loadJson = ContractFixtures.json("synthetic-many")
        s.open()
        assertEquals(300, s.rowIds().size)
        assertEquals(listOf("g0", "g1", "g2", "g3", "g4", "g5", "g6", "nested › g7", "nested › deeper › g8", "nested › deeper › g9"), s.groupTitles())
        s.dispatch(KatachiIntent.Search("T299"))
        assertEquals(listOf("nested.deeper.g9.T299"), s.rowIds().map { it.template })
        s.check(s.id("nested.deeper.g9.T299"))
        s.input(s.id("nested.deeper.g9.T299"), "name", "Last")
        s.generate()
        assertEquals("nested.deeper.g9.T299", s.lastArgs()["template"])
    }

    @Test
    fun `2つ目の定義モジュールに同じ役割名があればモジュールの帯で分かれそれぞれ別に生成する`() = runBlocking {
        val s = ScenarioHarness(this)
        s.loadJson = ContractFixtures.json("synthetic-structure")
        val second = module(":arch-b")
        s.addModule(second, ContractFixtures.json("synthetic-second"))
        s.open()
        val bands = s.ui().items.filterIsInstance<ListItemUi.ModuleHeader>()
        assertEquals(listOf(":arch-a", ":arch-b"), bands.map { it.title })
        assertEquals(listOf("0/7", "0/6"), bands.map { it.counter })
        val otherAllTypes = TemplateId(second.id, "AllTypes")
        assertTrue(otherAllTypes in s.rowIds())

        s.check(s.id("AllTypes"))
        s.check(otherAllTypes)
        s.input(s.id("AllTypes"), "name", "User")
        s.input(s.id("AllTypes"), "count", "1")
        s.input(s.id("AllTypes"), "mode", "Compact")
        s.input(otherAllTypes, "count", "2")
        s.input(otherAllTypes, "mode", "Compact")
        val result = s.generate()
        assertEquals(listOf(s.id("AllTypes"), otherAllTypes), result.report.templateIds)
        assertEquals(listOf("1", "2"), s.katachi.runs.map { it["count"] })
    }
}

/** [this] JSON as a katachi from before `modulePlacements` writes it: the key dropped. */
private fun String.withoutModulePlacements(): String {
    val start = indexOf(",\n  \"modulePlacements\"")
    check(start >= 0) { "no modulePlacements in the fixture" }
    return substring(0, start) + "\n}\n"
}
