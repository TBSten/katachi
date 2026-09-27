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
    private val repository = template("data/Repository", parameters = listOf(stringParam("item", default = "String"), stringParam("name")))
    private val broken = previewFailed("data/Cache")
    private val service = template("domain/Service")
    private val repositoryId = TemplateId(arch.id, repository.roleName)
    private val brokenId = TemplateId(arch.id, broken.roleName)
    private val serviceId = TemplateId(arch.id, service.roleName)

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
}
