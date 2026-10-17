package me.tbsten.katachi.intellij.ide.authoring.gutter

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.popup.PopupFactoryImpl
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.declaredAt
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.withTemplate

/**
 * F-11 of ide-authoring from the click on: the ◆'s navigation handler makes a popup (`createPopup`), and the popup is what a user
 * reads and chooses from, so the title, the list and the nested menus are held here, over the real platform popup.
 */
internal class DefinitionGutterPopupTest : DefinitionGutterTestBase() {
    /** The same helper called for two groups: `a.Service` and `b.Service`, written at the same lines. */
    private fun twoRolesOfOneHelper(): List<RoleModel> = listOf("a", "b").map { group ->
        roleModel(
            "$group.Service",
            layoutEntry("$group/*Service.kt", matchedFiles = listOf("$group/X.kt")).declaredAt(ROLE_FILE, 3).withTemplate("$group.Service.service", ROLE_FILE, 4, title = "Service"),
        ).declaredAt(ROLE_FILE, 2)
    }

    private fun marksOf(vararg roles: RoleModel): List<DrawnDefinitionMark> {
        writeFiles()
        openDefinition()
        snapshot = definitionSnapshot(definitionModel(*roles, projectRoot = root), module(":arch"))
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        return definitionMarks()
    }

    private fun texts(popup: ListPopup): List<String> = popup.listStep.values.map { (it as PopupFactoryImpl.ActionItem).text }

    private inline fun <T> ListPopup.use(block: (ListPopup) -> T): T = try { block(this) } finally { cancel() }

    fun `test 役割の ◆ の popup は 見出しに役割と件数を出し 行を速度検索の一覧に並べる`() {
        val menu = marksOf(serviceRole()).first().menu

        menu.createPopup(DataContext.EMPTY_CONTEXT).use { popup ->
            assertEquals("domain.Service — 3 files", popup.listStep.title)
            assertTrue(popup.listStep.isSpeedSearchEnabled)
            assertEquals(
                listOf("HealthService.kt", "PingService.kt", "UserService.kt", "+ Generate from this template…", "▶ Run the check (:arch:test)", "⟳ Reload Definition"),
                texts(popup),
            )
            assertFalse(popup.listStep.hasSubstep(popup.listStep.values.first()))
        }
    }

    fun `test template が2つある役割の ◆ の popup は Generate が入れ子になり 選べる行が2つ出る`() {
        val menu = marksOf(twoTemplateRole()).first().menu

        menu.createPopup(DataContext.EMPTY_CONTEXT).use { popup ->
            val generate = popup.listStep.values.single { (it as PopupFactoryImpl.ActionItem).text == "+ Generate from this template…" }
            assertTrue(popup.listStep.hasSubstep(generate))
        }
        val submenu = menu.actionGroup(DataContext.EMPTY_CONTEXT).getChildren(null).filterIsInstance<ActionGroup>().single()
        assertTrue(submenu.isPopup)
        assertEquals(listOf("Service", "Fake service"), submenu.getChildren(null).map { it.templatePresentation.text })
        (submenu.getChildren(null)[1] as DefinitionGutterMenu.RowAction).choose()
        assertEquals(listOf(TemplateId(arch, "domain.Service.fake")), effects.dialogs.map { it.initialTemplate })
    }

    fun `test 同じ template の行に2つの役割が結ばれたら tooltip も menu も 両方の template を出す`() {
        val mark = marksOf(*twoRolesOfOneHelper().toTypedArray()).last()
        assertEquals("katachi: generate from a.Service, b.Service", mark.tooltip)

        mark.menu.createPopup(DataContext.EMPTY_CONTEXT).use { popup ->
            assertEquals("generate from a.Service, b.Service", popup.listStep.title)
            assertEquals(listOf("+ Generate from this template…", "⊞ Show in the tool window"), texts(popup))
            assertTrue(popup.listStep.values.all { popup.listStep.hasSubstep(it) })
        }
        val submenus = mark.menu.actionGroup(DataContext.EMPTY_CONTEXT).getChildren(null).filterIsInstance<ActionGroup>()
        (submenus[0].getChildren(null)[1] as DefinitionGutterMenu.RowAction).choose()
        (submenus[1].getChildren(null)[0] as DefinitionGutterMenu.RowAction).choose()

        assertEquals(listOf(TemplateId(arch, "b.Service.service")), effects.dialogs.map { it.initialTemplate })
        assertEquals(listOf(TemplateId(arch, "a.Service.service")), effects.revealed)
    }
}
