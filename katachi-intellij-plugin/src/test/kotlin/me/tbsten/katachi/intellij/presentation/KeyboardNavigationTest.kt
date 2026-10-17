package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.cast
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.previewFailed
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class KeyboardNavigationTest {
    private val arch = module(":arch")
    private val repository = template("data.Repository", parameters = listOf(stringParam("item", default = "String"), stringParam("name")))
    private val broken = previewFailed("data.Cache")
    private val service = template("domain.Service")
    private val repositoryId = TemplateId(arch.id, repository.template)
    private val brokenId = TemplateId(arch.id, broken.template)
    private val serviceId = TemplateId(arch.id, service.template)

    private val base = KatachiScreenState(
        phase = ScreenPhase.Ready,
        modules = listOf(arch),
        snapshots = listOf(DescriptionSnapshot(arch, "0.3.0", listOf(repository, broken, service), Instant.EPOCH)),
    )

    private fun list(state: KatachiScreenState = base): ListUi =
        uiStateOf(state, JapaneseKatachiStrings, Instant.EPOCH).body.cast<BodyUi.Listing>().list

    private fun checked(vararg intents: KatachiIntent) = list(intents.fold(base) { s, i -> applyFormIntent(s, i)!! })

    @Test
    fun `行の上下は前後の行へ移り、先頭の行の上は検索欄へ移る`() {
        assertEquals(NavResult(focus = FocusMove.To(FocusTarget.Row(brokenId))), navigate(list(), FocusTarget.Row(repositoryId), NavKey.Down))
        assertEquals(NavResult(focus = FocusMove.To(FocusTarget.Search)), navigate(list(), FocusTarget.Row(repositoryId), NavKey.Up))
        assertFalse(navigate(list(), FocusTarget.Row(serviceId), NavKey.Down).isHandled)
    }

    @Test
    fun `検索欄の下は最初の行へ移る`() {
        assertEquals(NavResult(focus = FocusMove.To(FocusTarget.Row(repositoryId))), navigate(list(), FocusTarget.Search, NavKey.Down))
    }

    @Test
    fun `Space はチェックを切り替え、チェックできない行では何もしない`() {
        assertEquals(NavResult(intent = KatachiIntent.ToggleCheck(repositoryId)), navigate(list(), FocusTarget.Row(repositoryId), NavKey.Space))
        assertFalse(navigate(list(), FocusTarget.Row(brokenId), NavKey.Space).isHandled)
    }

    @Test
    fun `Enter はチェックを入れて最初の空の必須欄へ移る`() {
        val result = navigate(list(), FocusTarget.Row(repositoryId), NavKey.Enter)
        assertEquals(NavResult(KatachiIntent.ToggleCheck(repositoryId), FocusMove.FirstEmptyRequired(repositoryId)), result)
        val form = checked(KatachiIntent.ToggleCheck(repositoryId)).items.firstNotNullOf { (it as? ListItemUi.Row)?.row?.body as? RowBodyUi.Form }.form
        assertEquals(FieldId(repositoryId, "name"), firstEmptyRequiredOf(form))
    }

    @Test
    fun `右と左はチェック中の行のフォームを開閉する`() {
        val open = checked(KatachiIntent.ToggleCheck(repositoryId))
        assertEquals(NavResult(intent = KatachiIntent.SetExpanded(repositoryId, false)), navigate(open, FocusTarget.Row(repositoryId), NavKey.Left))
        val folded = checked(KatachiIntent.ToggleCheck(repositoryId), KatachiIntent.SetExpanded(repositoryId, false))
        assertEquals(NavResult(intent = KatachiIntent.SetExpanded(repositoryId, true)), navigate(folded, FocusTarget.Row(repositoryId), NavKey.Right))
        assertFalse(navigate(list(), FocusTarget.Row(repositoryId), NavKey.Right).isHandled)
    }

    @Test
    fun `欄で Esc を押すとその行へ戻る`() {
        val field = FieldId(repositoryId, "name")
        assertEquals(NavResult(focus = FocusMove.To(FocusTarget.Row(repositoryId))), navigate(list(), FocusTarget.Field(field), NavKey.Escape))
        assertFalse(navigate(list(), FocusTarget.Field(field), NavKey.Up).isHandled)
    }

    @Test
    fun `行で文字を打つと検索欄に入る`() {
        assertEquals(
            NavResult(KatachiIntent.Search("s"), FocusMove.To(FocusTarget.Search)),
            navigate(list(), FocusTarget.Row(repositoryId), NavKey.Type("s")),
        )
    }

    @Test
    fun `生成のショートカットは押せるときだけ生成する`() {
        assertFalse(navigate(list(), FocusTarget.Search, NavKey.Generate).isHandled)
        val ready = checked(KatachiIntent.ToggleCheck(serviceId), KatachiIntent.Input(FieldId(serviceId, "name"), "User"))
        assertEquals(NavResult(intent = KatachiIntent.Generate), navigate(ready, FocusTarget.Field(FieldId(serviceId, "name")), NavKey.Generate))
    }
    // Search field Esc (T-4).

    @Test
    fun `検索欄の Esc は検索語があれば消し、空なら何もしない`() {
        val searching = applyFormIntent(base, KatachiIntent.Search("repo"))!!
        assertEquals(NavResult(intent = KatachiIntent.Search("")), navigate(list(searching), FocusTarget.Search, NavKey.Escape))
        assertFalse(navigate(list(), FocusTarget.Search, NavKey.Escape).isHandled)
    }

    @Test
    fun `検索語を消す Esc の後に押しても何も起きない`() {
        val searching = applyFormIntent(base, KatachiIntent.Search("repo"))!!
        val cleared = applyFormIntent(searching, navigate(list(searching), FocusTarget.Search, NavKey.Escape).intent!!)!!
        assertFalse(navigate(list(cleared), FocusTarget.Search, NavKey.Escape).isHandled)
    }

    // Module bands (T-5).

    private val other = module(":other")
    private val otherService = template("domain.Other")
    private val otherId = TemplateId(other.id, otherService.template)
    private val two = base.copy(
        modules = listOf(arch, other),
        snapshots = base.snapshots + DescriptionSnapshot(other, "0.3.0", listOf(otherService), Instant.EPOCH),
    )
    private val archBand = FocusTarget.ModuleHeader(arch.id)
    private val otherBand = FocusTarget.ModuleHeader(other.id)

    @Test
    fun `帯は上下の並びに入る（検索欄の下は最初の帯、帯の下はその最初の行、最後の行の下は次の帯）`() {
        val l = list(two)
        assertEquals(NavResult(focus = FocusMove.To(archBand)), navigate(l, FocusTarget.Search, NavKey.Down))
        assertEquals(NavResult(focus = FocusMove.To(FocusTarget.Row(repositoryId))), navigate(l, archBand, NavKey.Down))
        assertEquals(NavResult(focus = FocusMove.To(otherBand)), navigate(l, FocusTarget.Row(serviceId), NavKey.Down))
        assertEquals(NavResult(focus = FocusMove.To(FocusTarget.Row(otherId))), navigate(l, otherBand, NavKey.Down))
        assertFalse(navigate(l, FocusTarget.Row(otherId), NavKey.Down).isHandled)
    }

    @Test
    fun `上は前の並びへ戻り、最初の帯の上は検索欄へ移る`() {
        val l = list(two)
        assertEquals(NavResult(focus = FocusMove.To(FocusTarget.Search)), navigate(l, archBand, NavKey.Up))
        assertEquals(NavResult(focus = FocusMove.To(archBand)), navigate(l, FocusTarget.Row(repositoryId), NavKey.Up))
        assertEquals(NavResult(focus = FocusMove.To(FocusTarget.Row(serviceId))), navigate(l, otherBand, NavKey.Up))
    }

    @Test
    fun `畳んだ帯の下は次の帯で、帯の Enter と Space は開閉する`() {
        val folded = list(applyFormIntent(two, KatachiIntent.ToggleModule(arch.id))!!)
        assertEquals(NavResult(focus = FocusMove.To(otherBand)), navigate(folded, archBand, NavKey.Down))
        val toggle = NavResult(intent = KatachiIntent.ToggleModule(arch.id))
        assertEquals(toggle, navigate(list(two), archBand, NavKey.Enter))
        assertEquals(toggle, navigate(list(two), archBand, NavKey.Space))
        assertEquals(toggle, navigate(folded, archBand, NavKey.Enter))
    }

    @Test
    fun `帯の右は畳んでいるときだけ開き、左は開いているときだけ畳む`() {
        val open = list(two)
        val folded = list(applyFormIntent(two, KatachiIntent.ToggleModule(arch.id))!!)
        val toggle = NavResult(intent = KatachiIntent.ToggleModule(arch.id))
        assertEquals(toggle, navigate(open, archBand, NavKey.Left))
        assertFalse(navigate(open, archBand, NavKey.Right).isHandled)
        assertEquals(toggle, navigate(folded, archBand, NavKey.Right))
        assertFalse(navigate(folded, archBand, NavKey.Left).isHandled)
    }

    @Test
    fun `帯では Esc も文字入力も生成も何もしない（生成は押せるときだけ全体で効く）`() {
        val l = list(two)
        assertFalse(navigate(l, archBand, NavKey.Escape).isHandled)
        assertFalse(navigate(l, archBand, NavKey.Type("s")).isHandled)
        assertFalse(navigate(l, archBand, NavKey.Generate).isHandled)
    }

    @Test
    fun `帯が 1 つだけなら帯は出ず並びは行だけ`() {
        val l = list()
        assertEquals(listOf<FocusTarget>(FocusTarget.Row(repositoryId), FocusTarget.Row(brokenId), FocusTarget.Row(serviceId)), stopsOf(l))
    }

    // Search focus request (T-3).

    @Test
    fun `検索欄へのフォーカス要求は番号が増え、画面の状態に載る`() {
        assertEquals(0, list().search.focusRequest)
        val once = applyFormIntent(base, KatachiIntent.FocusSearch)!!
        assertEquals(1, list(once).search.focusRequest)
        val twice = applyFormIntent(once, KatachiIntent.FocusSearch)!!
        assertEquals(2, list(twice).search.focusRequest)
    }

    @Test
    fun `フォーカス要求は検索語や入力を変えない`() {
        val typed = applyFormIntent(applyFormIntent(base, KatachiIntent.Search("repo"))!!, KatachiIntent.ToggleCheck(repositoryId))!!
        val asked = applyFormIntent(typed, KatachiIntent.FocusSearch)!!
        assertEquals(typed.copy(view = typed.view.copy(focusRequest = 1)), asked)
    }
}
