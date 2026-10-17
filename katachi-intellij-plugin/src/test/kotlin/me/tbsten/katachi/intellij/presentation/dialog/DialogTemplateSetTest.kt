package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.setMember
import me.tbsten.katachi.intellij.testing.setTemplate
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The generate dialog does not handle a template set (template-set design 8.4): a set is picked in the tool window. */
class DialogTemplateSetTest {
    private val arch = module(":arch-a")
    private val set = ModuleTemplate(arch, setTemplate("feature.NewFeature", listOf(setMember("feature.Screen", "Screen", "feature/\${feature}/ui/Screen.kt"))))
    private val broken = ModuleTemplate(arch, template("data.Broken", conflict = true))
    private val repository = ModuleTemplate(arch, template("data.Repository"))
    private val noSeeds: (me.tbsten.katachi.intellij.model.TemplateId) -> Map<String, String> = { emptyMap() }

    @Test
    fun `組は選択欄の候補に出ない`() {
        val usable = usableTemplatesOf(listOf(set, repository))

        assertEquals(listOf(repository.id), usable.map { it.id })
        assertEquals(listOf(repository.id), initialCoreOf(usable, repository.id, noSeeds).candidates.map { it.id })
    }

    @Test
    fun `初めのテンプレートが使えないとき代わりに組は選ばれず使えるテンプレートが選ばれる`() {
        val all = usableTemplatesOf(listOf(broken, set, repository))

        val core = initialCoreOf(all, broken.id, noSeeds)

        assertEquals(repository.id, core.selectedId)
    }

    @Test
    fun `組だけの一覧では何も選ばれず候補なしの案内になる`() {
        val core = initialCoreOf(usableTemplatesOf(listOf(set)), set.id, noSeeds)

        assertNull(core.selected)
        assertEquals(GenerateDialogListNotice.NoCandidates, core.listNotice)
    }

    @Test
    fun `一覧の更新で組が増えても候補には入らない`() {
        val core = initialCoreOf(usableTemplatesOf(listOf(repository)), repository.id, noSeeds)

        val next = listChanged(core, listOf(repository, set), noSeeds)

        assertEquals(listOf(repository.id), next.all.map { it.id })
    }
}
