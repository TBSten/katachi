package me.tbsten.katachi.intellij.uitest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.EmptyReason
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.presentation.LoadingState
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
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
 * The list's keyboard rules on the real Composables (T-3, T-4, T-5): the search field takes the focus when the IDE asks,
 * Esc empties it, and a module band is a stop of the arrow keys that Enter / Space / ← / → fold and open.
 * What the screen sends goes through the same pure transitions as the ViewModel's and is drawn again from the result.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardFocusInteractionTest {
    private val archA = module(":arch-a")
    private val archB = module(":arch-b")
    private val firstRow = TemplateId(archA.id, ContractFixtures.templates("arch-a").first().template)

    private class Host(initial: KatachiScreenState) {
        var state by mutableStateOf(initial)
        val sent = mutableListOf<KatachiIntent>()

        fun send(intent: KatachiIntent) {
            sent += intent
            applyFormIntent(state, intent)?.let { state = it }
        }
    }

    private fun loaded(phase: ScreenPhase = ScreenPhase.Ready) = KatachiScreenState(
        phase = phase,
        modules = listOf(archA, archB),
        snapshots = listOf(
            DescriptionSnapshot(archA, "0.3.0", ContractFixtures.templates("arch-a"), ScenarioHarness.NOW),
            DescriptionSnapshot(archB, "0.3.0", ContractFixtures.templates("arch-b"), ScenarioHarness.NOW),
        ),
    )

    private fun ComposeUiTest.show(host: Host) {
        setContent {
            IntUiTheme {
                Box(Modifier.requiredSize(420.dp, 720.dp).background(JewelTheme.globalColors.panelBackground)) {
                    KatachiToolWindowContent(uiStateOf(host.state, JapaneseKatachiStrings, ScenarioHarness.NOW), host::send)
                }
            }
        }
    }

    private fun ComposeUiTest.press(tag: String, key: Key) {
        onNodeWithTag(tag).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private val searchTag = KatachiTestTags.SEARCH
    private val bandA get() = KatachiTestTags.moduleBand(archA.id)
    private val bandB get() = KatachiTestTags.moduleBand(archB.id)

    @Test
    fun `IDE が頼むと検索欄にカーソルが来て、もう一度頼むとほかの欄からも戻ってくる`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(searchTag).assertIsNotFocused()

        runOnIdle { host.send(KatachiIntent.FocusSearch) }
        waitForIdle()
        onNodeWithTag(searchTag).assertIsFocused()

        onNodeWithTag(bandA).requestFocus()
        onNodeWithTag(searchTag).assertIsNotFocused()
        runOnIdle { host.send(KatachiIntent.FocusSearch) }
        waitForIdle()
        onNodeWithTag(searchTag).assertIsFocused()
    }

    @Test
    fun `一覧がまだ無い間の頼みは、一覧が出たときに 1 回だけ検索欄に当たる`() = runComposeUiTest {
        val host = Host(applyFormIntent(loaded(ScreenPhase.Initializing), KatachiIntent.FocusSearch)!!)
        show(host)
        runOnIdle { host.state = host.state.copy(phase = ScreenPhase.Ready) }
        waitForIdle()
        onNodeWithTag(searchTag).assertIsFocused()

        // The user moves on; the same request does not pull the cursor back while the list stays.
        onNodeWithTag(bandA).requestFocus()
        runOnIdle { host.send(KatachiIntent.Search("")) }
        waitForIdle()
        onNodeWithTag(searchTag).assertIsNotFocused()
    }

    @Test
    fun `一度当たった頼みは、一覧が エラー・空・初回読み込み を挟んで出直しても検索欄に当て直さない`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        runOnIdle { host.send(KatachiIntent.FocusSearch) }
        waitForIdle()
        onNodeWithTag(searchTag).assertIsFocused()

        val detours = listOf<(KatachiScreenState) -> KatachiScreenState>(
            { it.copy(phase = ScreenPhase.LoadError(LoadFailure.Gradle(GradleFailure.CompilationFailed(listOf("e: boom"))), emptyList())) },
            { it.copy(phase = ScreenPhase.Empty(EmptyReason.NoTemplates)) },
            { it.copy(loading = LoadingState(isInitial = true)) },
        )
        for (detour in detours) {
            onNodeWithTag(bandA).requestFocus()
            val listing = host.state
            runOnIdle { host.state = detour(listing) }
            waitForIdle()
            onNodeWithTag(searchTag).assertDoesNotExist()
            runOnIdle { host.state = listing }
            waitForIdle()
            onNodeWithTag(searchTag).assertIsNotFocused()
        }
    }

    @Test
    fun `検索欄の Esc は検索語を消し、空のときは何も送らない`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(searchTag).performTextInput("repo")
        waitForIdle()
        assertEquals("repo", host.state.searchQuery)

        press(searchTag, Key.Escape)
        assertEquals("", host.state.searchQuery)
        onNodeWithTag(searchTag).assertEditableTextIs("")
        onNodeWithTag(searchTag).assertIsFocused()

        val before = host.sent.size
        press(searchTag, Key.Escape)
        assertEquals(before, host.sent.size)
    }

    @Test
    fun `検索欄の下で最初の帯へ、帯の下でその最初の行へ、上で戻れる`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(searchTag).requestFocus()

        press(searchTag, Key.DirectionDown)
        onNodeWithTag(bandA).assertIsFocused()
        press(bandA, Key.DirectionDown)
        onNodeWithTag(KatachiTestTags.check(firstRow)).assertIsFocused()
        press(KatachiTestTags.check(firstRow), Key.DirectionUp)
        onNodeWithTag(bandA).assertIsFocused()
        press(bandA, Key.DirectionUp)
        onNodeWithTag(searchTag).assertIsFocused()
    }

    @Test
    fun `帯の Enter と Space は開閉し、フォーカスは帯に残る`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(bandA).requestFocus()

        press(bandA, Key.Enter)
        assertTrue(archA.id in host.state.view.collapsedModules)
        onNodeWithTag(bandA).assertIsFocused()
        press(bandA, Key.Spacebar)
        assertTrue(archA.id !in host.state.view.collapsedModules)
        onNodeWithTag(bandA).assertIsFocused()
    }

    @Test
    fun `帯の左は畳み、右は開き、既に畳んだ・開いた状態では何も送らない`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(bandA).requestFocus()

        var sent = host.sent.size
        press(bandA, Key.DirectionRight)
        assertEquals("開いている帯の → は何もしない", sent, host.sent.size)
        press(bandA, Key.DirectionLeft)
        assertTrue(archA.id in host.state.view.collapsedModules)
        sent = host.sent.size
        press(bandA, Key.DirectionLeft)
        assertEquals("畳んだ帯の ← は何もしない", sent, host.sent.size)
        press(bandA, Key.DirectionRight)
        assertTrue(archA.id !in host.state.view.collapsedModules)
    }

    @Test
    fun `畳んだ帯の下は次の帯へ移る`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(bandA).requestFocus()
        press(bandA, Key.DirectionLeft)

        press(bandA, Key.DirectionDown)
        onNodeWithTag(bandB).assertIsFocused()
    }
}

private fun SemanticsNodeInteraction.assertEditableTextIs(expected: String) =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))
