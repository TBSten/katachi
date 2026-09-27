package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.cast
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The definition changing shape between two generations: a parameter's type, a new module. */
class ReloadChangesScenarioTest {
    private val base = ContractFixtures.json("arch-a")

    /** arch-a where Repository's `item` is an Int instead of a String. */
    private val itemAsInt = base.replaceFirst(
        "\"name\": \"item\",\n          \"kind\": \"StringParameter\",\n          \"typeName\": \"String\",\n          \"default\": \"String\"",
        "\"name\": \"item\",\n          \"kind\": \"IntParameter\",\n          \"typeName\": \"Int\",\n          \"default\": \"1\"",
    ).also { check(it != base) { "the fixture's item parameter moved" } }

    /** arch-a without `misc/NoArgs`. */
    private val withoutNoArgs = withoutTemplate(withoutTemplate(base, "misc/NoArgs"), "misc/NoArgs")

    private val archB = module(":arch-b")
    private val archBRepository = TemplateId(archB.id, "data/Repository")

    @Test
    fun `2回目の生成の前に再読み込みで引数の型が文字列から整数に変わると残った入力は欄の下で誤りになり直すまで生成できない`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.check(s.repository)
        s.input(s.repository, "name", "User")
        s.input(s.repository, "item", "Long")
        s.generate()
        s.dispatch(KatachiIntent.ContinueGenerating)
        s.input(s.repository, "name", "Order")
        s.input(s.repository, "item", "Long")

        s.reload(itemAsInt)
        val item = s.textField(s.repository, "item")
        assertTrue(item.isNumber)
        assertNull("an Int field has no multi-line mode", item.isMultiline)
        assertEquals("Long", item.value)
        assertTrue(item.error.orEmpty().startsWith("整数で入力してください"))
        assertFalse(s.formFooter().generateEnabled)
        assertEquals("Repository: item は整数で入力してください", s.formFooter().reason)

        s.input(s.repository, "item", "3")
        s.generate()
        assertEquals("3", s.lastArgs()["item"])
        assertEquals("Order", s.lastArgs()["name"])
    }

    @Test
    fun `再読み込みで定義モジュールが増えるとモジュールの帯が出て前の選択と入力は残り増えた方も生成できる`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.check(s.repository)
        s.input(s.repository, "name", "User")
        s.generate()
        s.dispatch(KatachiIntent.ContinueGenerating)
        s.input(s.repository, "name", "Order")

        s.addModule(archB, ContractFixtures.json("arch-b"))
        s.reload()
        val headers = s.ui().items.filterIsInstance<ListItemUi.ModuleHeader>()
        assertEquals(listOf(":arch-a", ":arch-b"), headers.map { it.title })
        assertEquals(listOf("1/7", "0/1"), headers.map { it.counter })
        assertEquals("Order", s.textField(s.repository, "name").value)

        s.check(archBRepository)
        // Same name, different type (String and Int): not linked (E-14), so it stays empty.
        assertEquals("", s.textField(archBRepository, "name").value)
        assertEquals(LinkUi.None, s.textField(archBRepository, "name").link)
        s.input(archBRepository, "name", "2")
        val result = s.generate()
        assertEquals(listOf(s.repository, archBRepository), result.report.items.map { it.templateId })
    }

    @Test
    fun `一覧を出している間の同期完了で定義モジュールが増えると探し直して一覧に加える`() = manual { dispatcher, s ->
        s.dispatch(KatachiIntent.Opened)
        dispatcher.runAll()
        s.check(s.repository)
        s.input(s.repository, "name", "User")

        s.addModule(archB, ContractFixtures.json("arch-b"))
        s.dispatch(KatachiIntent.SyncCompleted)
        dispatcher.runAll()
        assertEquals(2, s.loads)
        assertNull(s.state.loading)
        assertEquals(listOf(":arch-a", ":arch-b"), s.ui().items.filterIsInstance<ListItemUi.ModuleHeader>().map { it.title })
        assertEquals("User", s.textField(s.repository, "name").value)
    }

    @Test
    fun `読み込みを待つ生成の間にその読み込みでチェックした行が消えると進み具合の分母からも外れ残りの行だけ生成する`() = manual { dispatcher, s ->
        s.dispatch(KatachiIntent.Opened)
        dispatcher.runAll()
        s.check(s.repository)
        s.input(s.repository, "name", "User")
        s.check(s.noArgs)
        val gate = CompletableDeferred<Unit>()
        s.loadGate = gate
        s.loadJson = withoutNoArgs
        s.dispatch(KatachiIntent.Reload)
        dispatcher.runAll()
        s.dispatch(KatachiIntent.Generate)
        dispatcher.runAll()
        assertEquals(listOf(s.repository, s.noArgs), s.state.generation.cast<GenerationState.Running>().rows)

        // Every running state after the load, not only the last one: none may still count NoArgs.
        val runningRows = mutableListOf<List<TemplateId>>()
        dispatcher.afterTask = { (s.state.generation as? GenerationState.Running)?.let { runningRows += it.rows } }
        gate.complete(Unit)
        dispatcher.runAll()
        assertTrue(runningRows.isNotEmpty())
        assertTrue(runningRows.toString(), runningRows.all { it == listOf(s.repository) })
        val result = s.state.generation.cast<GenerationState.Finished>()
        assertEquals(listOf(s.repository), result.report.items.map { it.templateId })
    }

    @Test
    fun `同期完了で定義モジュールが変わらなければGradleを走らせない`() = manual { dispatcher, s ->
        s.dispatch(KatachiIntent.Opened)
        dispatcher.runAll()
        assertEquals(1, s.loads)

        s.dispatch(KatachiIntent.SyncCompleted)
        // Everything the sync queued has run: a load it started would have counted by now.
        dispatcher.runAll()
        assertEquals(0, dispatcher.pending)
        assertEquals(1, s.loads)
    }

    /** Runs [body] on a [ManualDispatcher]: each `runAll()` plays every queued step to the end, with no real time involved. */
    private fun manual(body: (ManualDispatcher, ScenarioHarness) -> Unit) {
        val dispatcher = ManualDispatcher()
        val scope = CoroutineScope(dispatcher + Job())
        try {
            body(dispatcher, ScenarioHarness(scope, ioDispatcher = dispatcher))
        } finally {
            scope.cancel()
        }
    }
}

/** [json] without the first object whose `roleName` is [roleName] (in `templates` or `details`), found by matching braces. */
private fun withoutTemplate(json: String, roleName: String): String {
    val key = json.indexOf("\"roleName\": \"$roleName\"")
    check(key >= 0) { "no $roleName" }
    val start = json.lastIndexOf('{', key)
    var depth = 0
    var end = start
    while (true) {
        when (json[end]) {
            '{' -> depth++
            '}' -> if (--depth == 0) break
        }
        end++
    }
    // Drop the comma that separated it from the next element, or the one before it when it was last.
    val after = json.substring(end + 1)
    return if (after.trimStart().startsWith(",")) {
        json.substring(0, start) + after.trimStart().removePrefix(",")
    } else {
        json.substring(0, start).trimEnd().removeSuffix(",") + after
    }
}
