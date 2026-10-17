package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.setMember
import me.tbsten.katachi.intellij.testing.setTemplate
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `RevealAndSeed` (New › katachi › a template set): the row checked and open, the empty fields seeded, typed values kept. */
class RevealAndSeedTest {
    private val arch = module(":arch-a")
    private val newScreen = setTemplate(
        "feature.NewScreen",
        listOf(setMember("feature.Screen", "Screen", "feature/\${feature}/ui/\${name}Screen.kt")),
        parameters = listOf(stringParam("feature"), stringParam("name")),
    )
    private val broken = setTemplate("feature.Broken", listOf(setMember("feature.Screen", "Screen", "x")), unavailable = true)
    private val setRow = ModuleTemplate(arch, newScreen)
    private val brokenRow = ModuleTemplate(arch, broken)

    private fun stateOf(form: FormState = FormState()) = KatachiScreenState(
        phase = ScreenPhase.Ready,
        modules = listOf(arch),
        snapshots = listOf(DescriptionSnapshot(arch, "0.3.0", listOf(newScreen, broken, template("data.Repository")), DEFINITION_INSTANT)),
        form = form,
    )

    private fun field(name: String) = FieldId(setRow.id, name)

    @Test
    fun `組の行にチェックと展開と強調が付き空の欄に決まった値が入る`() {
        val next = revealAndSeed(stateOf(), setRow.id, mapOf("feature" to "home"))

        assertTrue(next.form.isSelected(setRow.id))
        assertTrue(setRow.id in next.form.expanded)
        assertEquals("home", next.form.inputOf(field("feature")))
        assertNull(next.form.inputOf(field("name")))
        assertEquals(setRow.id, next.view.highlight?.templateId)
    }

    @Test
    fun `既に打った値は上書きしない`() {
        val typed = inputField(stateOf().form, stateOf().rows, field("feature"), "settings")

        val next = revealAndSeed(stateOf(typed), setRow.id, mapOf("feature" to "home"))

        assertEquals("settings", next.form.inputOf(field("feature")))
    }

    @Test
    fun `既にチェックした行はチェックを外さない`() {
        val checked = toggleCheck(stateOf().form, stateOf().rows, setRow.id)

        val next = revealAndSeed(stateOf(checked), setRow.id, emptyMap())

        assertTrue(next.form.isSelected(setRow.id))
    }

    @Test
    fun `組に無い名前の値は入れない`() {
        val next = revealAndSeed(stateOf(), setRow.id, mapOf("nothing" to "x"))

        assertEquals(null, next.form.inputOf(field("nothing")))
    }

    @Test
    fun `使えない組や一覧に無い行は強調だけでチェックしない`() {
        val unavailable = revealAndSeed(stateOf(), brokenRow.id, mapOf("feature" to "home"))
        val missing = revealAndSeed(stateOf(), setRow.id.copy(template = "feature.Gone"), mapOf("feature" to "home"))

        assertFalse(unavailable.form.isSelected(brokenRow.id))
        assertEquals(brokenRow.id, unavailable.view.highlight?.templateId)
        assertTrue(missing.form.selected.isEmpty())
        assertEquals("feature.Gone", missing.view.highlight?.templateId?.template)
    }

    @Test
    fun `生成中は強調だけでフォームは変わらない`() {
        val running = stateOf().copy(generation = GenerationState.Running(emptyList(), emptyMap()))

        val next = revealAndSeed(running, setRow.id, mapOf("feature" to "home"))

        assertEquals(running.form, next.form)
    }

    @Test
    fun `結果が出ているときに組を選ぶと結果を閉じてからチェックと展開と値を入れる`() {
        val finished = stateOf().copy(generation = GenerationState.Finished(GenerationReport(emptyList()), localHistoryLabel = null))

        val next = revealAndSeed(finished, setRow.id, mapOf("feature" to "home"))

        assertNull(next.generation)
        assertTrue(next.form.isSelected(setRow.id))
        assertTrue(setRow.id in next.form.expanded)
        assertEquals("home", next.form.inputOf(field("feature")))
    }

    @Test
    fun `ScreenTransitions の意図からも同じに届く`() {
        val next = applyFormIntent(stateOf(), KatachiIntent.RevealAndSeed(setRow.id, mapOf("feature" to "home")))

        assertEquals("home", next?.form?.inputOf(field("feature")))
    }
}
