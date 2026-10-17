package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.actionSystem.AnActionEvent
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.ide.dialog.GenerateDialogs
import me.tbsten.katachi.intellij.ide.newmenu.KatachiNewGroup
import me.tbsten.katachi.intellij.ide.newmenu.RevealSetAction
import me.tbsten.katachi.intellij.ide.newmenu.TemplateItemAction
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.KatachiIntent

/**
 * New › katachi › a template set, all of it real but Gradle and the tool window's activation: the item opens the tool window
 * (not the dialog) with the set's row checked and open, and the values the directory decided in its fields; what was typed stays.
 * The fixture is `synthetic-template-set` as `:arch-a`'s list: `Starter`, whose first member's file is
 * `src/main/kotlin/feature/${name}/${name}Screen.kt`.
 */
internal class EntryToGenerationSetTest : EntryToGenerationTestBase() {
    private val featureDir get() = archDir.resolve("src/main/kotlin/feature")

    override fun setUp() {
        super.setUp()
        gradle.fixtureOf["arch-a"] = "synthetic-template-set"
    }

    private fun setItemOn(directoryName: String): Pair<RevealSetAction, AnActionEvent> {
        val group = KatachiNewGroup()
        val event = menuEventOn(group, directoryOf(featureDir.resolve(directoryName)))
        group.update(event)
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        group.update(event)
        val leaves = group.getChildren(event).flattened()
        assertTrue(
            "the set is not a dialog item",
            leaves.filterIsInstance<TemplateItemAction>().none { it.templatePresentation.text == "はじめの一式" },
        )
        return leaves.filterIsInstance<RevealSetAction>().first { it.templatePresentation.text == "はじめの一式" } to event
    }

    private fun starter(): TemplateId = service.viewModel.state.value.rows.first { it.template.template == "Starter" }.id

    // covers: 論点0
    fun `test New メニューの組の項目はダイアログを開かずツールウィンドウに組の行をチェックと展開で出しディレクトリの値を入れる`() {
        val (item, event) = setItemOn("home")
        GenerateDialogs.setTestAnswer({ opened -> fail("the set opened the dialog: $opened"); null }, testRootDisposable)

        item.actionPerformed(event)

        val state = service.viewModel.state.value
        assertTrue(state.form.isSelected(starter()))
        assertTrue(starter() in state.form.expanded)
        assertEquals("home", state.form.inputOf(FieldId(starter(), "name")))
        assertEquals(starter(), state.view.highlight?.templateId)
        assertFalse("the menu writes nothing", gradleKinds().contains("generate"))
    }

    // covers: 論点0
    fun `test 既に打った値は New メニューの組の項目で上書きしない`() {
        val (item, event) = setItemOn("home")
        service.viewModel.dispatch(KatachiIntent.Input(FieldId(starter(), "name"), "Profile"))

        item.actionPerformed(event)

        assertEquals("Profile", service.viewModel.state.value.form.inputOf(FieldId(starter(), "name")))
        assertTrue(service.viewModel.state.value.form.isSelected(starter()))
    }
}
