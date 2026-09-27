package me.tbsten.katachi.intellij.uitest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.ListItemUi
import me.tbsten.katachi.intellij.presentation.BodyUi
import me.tbsten.katachi.intellij.presentation.ScreenPhase
import me.tbsten.katachi.intellij.presentation.applyFormIntent
import me.tbsten.katachi.intellij.presentation.uiStateOf
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real Composables, operated as a user would (typing, deleting, pasting, clicking), on
 * standalone Jewel. What they send goes through the same pure transitions as the ViewModel's, and
 * the screen is drawn again from the result, so each test sees both the intents that arrived and
 * what the screen then shows.
 */
@OptIn(ExperimentalTestApi::class)
class ToolWindowInteractionTest {
    private val arch = module(":arch-a")
    private val repository = TemplateId(arch.id, "data/Repository")
    private val useCase = TemplateId(arch.id, "domain/UseCase")
    private val repositoryName = FieldId(repository, "name")

    /** Holds the state as the ViewModel would for the intents that need no Gradle, and records every intent. */
    private class Host(initial: KatachiScreenState) {
        var state by mutableStateOf(initial)
        val sent = mutableListOf<KatachiIntent>()

        fun send(intent: KatachiIntent) {
            sent += intent
            applyFormIntent(state, intent)?.let { state = it }
        }
    }

    private fun loaded() = KatachiScreenState(
        phase = ScreenPhase.Ready,
        modules = listOf(arch),
        snapshots = listOf(DescriptionSnapshot(arch, "0.3.0", ContractFixtures.templates("arch-a"), ScenarioHarness.NOW)),
    )

    private fun ComposeUiTest.show(host: Host, width: Int = 420) {
        setContent {
            IntUiTheme {
                Box(Modifier.requiredSize(width.dp, 720.dp).background(JewelTheme.globalColors.panelBackground)) {
                    KatachiToolWindowContent(uiStateOf(host.state, JapaneseKatachiStrings, ScenarioHarness.NOW), host::send)
                }
            }
        }
    }

    private fun Host.shownRoles(): List<String> {
        val list = (uiStateOf(state, JapaneseKatachiStrings, ScenarioHarness.NOW).body as BodyUi.Listing).list
        return list.items.mapNotNull { (it as? ListItemUi.Row)?.row?.id?.roleName }
    }

    @Test
    fun `検索欄に打つと絞り込み空にすると全件に戻り欄も空になる`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        val all = host.shownRoles()
        onNodeWithTag(KatachiTestTags.SEARCH).performTextInput("repo")
        waitForIdle()
        assertEquals("repo", host.state.searchQuery)
        assertEquals(listOf("data/Repository"), host.shownRoles())
        onNodeWithText("UseCase").assertDoesNotExist()

        onNodeWithTag(KatachiTestTags.SEARCH).performTextClearance()
        waitForIdle()
        assertEquals("", host.state.searchQuery)
        assertEquals(all, host.shownRoles())
        onNodeWithText("UseCase").assertExists()
        onNodeWithTag(KatachiTestTags.SEARCH).assertEditableText("")
    }

    @Test
    fun `検索欄に打ってすぐ消しても同じフレームのうちなら空が届き絞られたままにならない`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        mainClock.autoAdvance = false
        onNodeWithTag(KatachiTestTags.SEARCH).performTextInput("r")
        onNodeWithTag(KatachiTestTags.SEARCH).performTextClearance()
        mainClock.advanceTimeByFrame()
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals("", host.state.searchQuery)
        assertTrue(host.shownRoles().size > 1)
    }

    @Test
    fun `一致0件のクリアを押すと検索が空になり全件と空の検索欄に戻る`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(KatachiTestTags.SEARCH).performTextInput("zzz")
        waitForIdle()
        onNodeWithText("「zzz」に一致するテンプレートはありません").assertExists()
        onNodeWithText("検索をクリア").performClick()
        waitForIdle()
        assertEquals(KatachiIntent.Search(""), host.sent.last())
        onNodeWithTag(KatachiTestTags.SEARCH).assertEditableText("")
        onNodeWithText("Repository").assertExists()
    }

    @Test
    fun `チェックを押すとフォームが開き入力欄に打つ消す貼ると値がそのまま届く`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(KatachiTestTags.check(repository)).performClick()
        waitForIdle()
        assertEquals(KatachiIntent.ToggleCheck(repository), host.sent.last())
        onNodeWithTag(KatachiTestTags.check(repository)).assertIsOn()

        val name = onNodeWithTag(KatachiTestTags.field(repositoryName))
        name.performTextInput("User")
        waitForIdle()
        assertEquals("User", host.state.form.inputOf(repositoryName))
        name.performTextClearance()
        waitForIdle()
        assertEquals("", host.state.form.inputOf(repositoryName))
        onNodeWithText("入力してください").assertExists()
        // A paste replaces the selection with the clipboard's text in one edit.
        name.performTextReplacement("OrderHistory")
        waitForIdle()
        assertEquals("OrderHistory", host.state.form.inputOf(repositoryName))
        name.assertEditableText("OrderHistory")
        onNodeWithText("入力してください").assertDoesNotExist()
    }

    @Test
    fun `同名の欄に打つともう一方の欄の表示も書き換わる`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(KatachiTestTags.check(repository)).performClick()
        onNodeWithTag(KatachiTestTags.check(useCase)).performClick()
        waitForIdle()
        onNodeWithTag(KatachiTestTags.field(repositoryName)).performTextInput("User")
        waitForIdle()
        onNodeWithTag(KatachiTestTags.field(FieldId(useCase, "name"))).assertEditableText("User")
    }

    @Test
    fun `真偽の欄を押すと反対の値が届き分岐で引数の欄が畳まれる`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(KatachiTestTags.check(repository)).performClick()
        waitForIdle()
        onNodeWithTag(KatachiTestTags.field(FieldId(repository, "implSuffix"))).assertExists()
        onNodeWithTag(KatachiTestTags.field(FieldId(repository, "withImpl"))).assertIsOn().performClick()
        waitForIdle()
        assertEquals(KatachiIntent.Input(FieldId(repository, "withImpl"), "false"), host.sent.last())
        onNodeWithTag(KatachiTestTags.field(FieldId(repository, "withImpl"))).assertIsOff()
        onNodeWithTag(KatachiTestTags.field(FieldId(repository, "implSuffix"))).assertDoesNotExist()
        onNodeWithText("implSuffix（withImpl がオンのとき）").assertExists()
    }

    @Test
    fun `文字列の欄の切り替えを押すと複数行になり落ちずに描け向きのツールチップが変わる`() = runComposeUiTest {
        val host = Host(loaded())
        show(host, width = 300)
        onNodeWithTag(KatachiTestTags.check(repository)).performClick()
        waitForIdle()
        onNodeWithTag(KatachiTestTags.multiline(repositoryName)).assert(hasContentDescription("複数行で入力")).performClick()
        waitForIdle()
        assertEquals(KatachiIntent.ToggleMultiline(repositoryName), host.sent.last())
        onNodeWithTag(KatachiTestTags.multiline(repositoryName)).assert(hasContentDescription("1行に戻す"))
        onNodeWithTag(KatachiTestTags.field(repositoryName)).performTextInput("line1\nline2")
        waitForIdle()
        assertEquals("line1\nline2", host.state.form.inputOf(repositoryName))
        onNodeWithTag(KatachiTestTags.multiline(repositoryName)).performClick()
        waitForIdle()
        onNodeWithTag(KatachiTestTags.multiline(repositoryName)).assert(hasContentDescription("複数行で入力"))
    }

    @Test
    fun `必須が埋まるまで生成は押せず埋めて押すと生成が届く`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(KatachiTestTags.check(repository)).performClick()
        waitForIdle()
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsNotEnabled()
        onNodeWithTag(KatachiTestTags.field(repositoryName)).performTextInput("User")
        waitForIdle()
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(KatachiIntent.Generate, host.sent.last())
    }
}

/** The text in the field itself, without its placeholder (which the node's Text also holds). */
private fun SemanticsNodeInteraction.assertEditableText(expected: String): SemanticsNodeInteraction =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))
