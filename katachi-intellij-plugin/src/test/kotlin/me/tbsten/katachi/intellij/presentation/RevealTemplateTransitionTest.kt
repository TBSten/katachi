package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.patternTemplate
import me.tbsten.katachi.intellij.testing.snapshotOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [View template] of an editor notification as the ViewModel applies it (C2): which row carries the
 * highlight, what it takes away so that the row shows, and what it leaves alone.
 */
class RevealTemplateTransitionTest {
    private fun ScenarioHarness.highlighted(): List<TemplateId> = ui().items.mapNotNull { item ->
        (item as? ListItemUi.Row)?.row?.takeIf { it.highlight != null }?.id
    }

    // covers: 検証の計画 ジャンプ・強調
    @Test
    fun `強調するのは頼まれた1行だけでチェックと入力は変わらない`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.check(s.repository)
        s.input(s.repository, "name", "User")
        val form = s.state.form

        s.dispatch(KatachiIntent.RevealTemplate(s.useCase))

        assertEquals(listOf(s.useCase), s.highlighted())
        assertEquals(form, s.state.form)
    }

    // covers: 検証の計画 ジャンプ・強調
    @Test
    fun `同じ行をもう一度頼むと番号が進み別の行を頼むと強調が移る`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()

        s.dispatch(KatachiIntent.RevealTemplate(s.useCase))
        val first = s.row(s.useCase).highlight!!
        s.dispatch(KatachiIntent.RevealTemplate(s.useCase))
        val second = s.row(s.useCase).highlight!!
        s.dispatch(KatachiIntent.RevealTemplate(s.repository))

        assertTrue("the same row asked again must scroll again: $first then $second", second > first)
        assertEquals(listOf(s.repository), s.highlighted())
        assertNull(s.row(s.useCase).highlight)
    }

    // covers: 検証の計画 ジャンプ・強調
    @Test
    fun `検索で隠れた行を頼むと検索を空にし見えている行なら検索はそのまま`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.dispatch(KatachiIntent.Search("repo"))

        s.dispatch(KatachiIntent.RevealTemplate(s.repository))
        assertEquals("repo", s.state.searchQuery)

        s.dispatch(KatachiIntent.RevealTemplate(s.useCase))
        assertEquals("", s.state.searchQuery)
        assertEquals(listOf(s.useCase), s.highlighted())
    }

    // covers: 検証の計画 ジャンプ・強調
    @Test
    fun `畳まれたモジュールの行を頼むとそのモジュールだけ開く`() {
        val a = module(":arch-a")
        val b = module(":arch-b")
        val repository = patternTemplate("data.Repository", "data/\${name}Repository.kt")
        val state = KatachiScreenState(
            phase = ScreenPhase.Ready,
            modules = listOf(a, b),
            snapshots = listOf(snapshotOf(a, listOf(repository)), snapshotOf(b, listOf(repository))),
        ).let { applyFormIntent(it, KatachiIntent.ToggleModule(a.id))!! }
            .let { applyFormIntent(it, KatachiIntent.ToggleModule(b.id))!! }

        val revealed = applyFormIntent(state, KatachiIntent.RevealTemplate(TemplateId(b.id, "data.Repository")))!!

        assertEquals(setOf(a.id), revealed.view.collapsedModules)
    }

    // covers: 検証の計画 ジャンプ・強調
    @Test
    fun `一覧を読み込む前に頼んだ強調は読み込んだ後の行に付く`() = runBlocking {
        val s = ScenarioHarness(this)
        s.dispatch(KatachiIntent.RevealTemplate(s.useCase))

        s.open()

        assertEquals(listOf(s.useCase), s.highlighted())
    }
}
