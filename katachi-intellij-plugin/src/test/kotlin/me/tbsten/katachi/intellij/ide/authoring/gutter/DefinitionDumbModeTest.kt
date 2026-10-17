package me.tbsten.katachi.intellij.ide.authoring.gutter

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.ui.popup.PopupFactoryImpl

/**
 * F-14 of ide-authoring for the ◆: the definition file's ◆ are drawn while the project is indexing, the same as when it is not (design
 * 4.3). The completion and the annotation are held to it by their own tests (`DefinitionCompletionTest`,
 * `DefinitionErrorAnnotatorTest`).
 */
internal class DefinitionDumbModeTest : DefinitionGutterTestBase() {
    fun `test 索引の最中でも ◆ は 索引が終わっているときと同じものが出る`() {
        writeFiles()
        val file = openDefinition()
        readDefinition()
        val smart = collectedDirectly(file, using = definitionProvider)

        assertTrue("the provider is dumb aware", DumbService.isDumbAware(definitionProvider))
        val dumb = DumbModeTestUtils.computeInDumbModeSynchronously(project) { collectedDirectly(file, using = definitionProvider) }

        assertEquals(2, smart.size)
        assertEquals(smart, dumb)
    }

    fun `test 索引の最中でも ◆ のメニューは開ける`() {
        writeFiles()
        openDefinition()
        readDefinition()
        val infos = collectedInfos(myFixture.file, using = definitionProvider)

        val rows = DumbModeTestUtils.computeInDumbModeSynchronously(project) {
            (infos.first().navigationHandler as DefinitionGutterMenu).shown()
        }

        assertEquals("HealthService.kt | core/service", rows.first())
    }

    fun `test 索引の最中でも ◆ の popup は開け 入れ子の Generate は入れ子のままで 全部の行が出る`() {
        writeFiles()
        openDefinition()
        readDefinition(twoTemplateRole())
        val infos = collectedInfos(myFixture.file, using = definitionProvider)

        val texts = DumbModeTestUtils.computeInDumbModeSynchronously(project) {
            val popup = (infos.first().navigationHandler as DefinitionGutterMenu).createPopup(DataContext.EMPTY_CONTEXT)
            try {
                val generate = popup.listStep.values.single { (it as PopupFactoryImpl.ActionItem).text == "+ Generate from this template…" }
                assertTrue("the sub menu is a sub menu while indexing", popup.listStep.hasSubstep(generate))
                popup.listStep.values.map { (it as PopupFactoryImpl.ActionItem).text }
            } finally {
                popup.cancel()
            }
        }

        assertEquals(listOf("HealthService.kt", "PingService.kt", "UserService.kt", "+ Generate from this template…", "▶ Run the check (:arch:test)", "⟳ Reload Definition"), texts)
    }
}
