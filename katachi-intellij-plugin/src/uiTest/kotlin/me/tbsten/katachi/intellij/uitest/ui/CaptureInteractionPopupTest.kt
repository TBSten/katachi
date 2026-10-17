package me.tbsten.katachi.intellij.uitest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.CandidateList
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The candidate popup of a capture field in the tool window, operated through the real Composables with the keys a user presses:
 * Down opens it, Up / Down move the highlight, Enter takes the highlighted candidate, Esc closes only the popup, and with it
 * closed Enter does nothing (as in any field; Cmd+Enter generates) and Esc returns the focus to the row. A popup that is open generates nothing, whatever key is pressed.
 */
@OptIn(ExperimentalTestApi::class)
class CaptureInteractionPopupTest {
    private val arch = module(":arch-a")
    private val viewModel = TemplateId(arch.id, "feature.ViewModel")
    private val feature = FieldId(viewModel, "feature")
    private val name = FieldId(viewModel, "name")
    private val featureTag = KatachiTestTags.field(feature)

    private class Host(initial: KatachiScreenState) {
        var state by mutableStateOf(initial)
        val sent = mutableListOf<KatachiIntent>()

        fun send(intent: KatachiIntent) {
            sent += intent
            applyFormIntent(state, intent)?.let { state = it }
        }

        val generated: Boolean get() = sent.any { it == KatachiIntent.Generate }
    }

    private fun loaded(candidates: CandidateList = CandidateList(listOf("home", "settings", "profile"), directoryPattern = "feature/{value}")) =
        KatachiScreenState(
            phase = ScreenPhase.Ready,
            modules = listOf(arch),
            snapshots = listOf(DescriptionSnapshot(arch, "0.3.0", ContractFixtures.templates("capture"), ScenarioHarness.NOW)),
            captureCandidates = mapOf(feature to candidates),
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

    /** The form of the template open, `name` filled so that only `feature` stands between the user and Generate. */
    private fun ComposeUiTest.openForm(host: Host) {
        show(host)
        onNodeWithTag(KatachiTestTags.check(viewModel)).performClick()
        waitForIdle()
        inputOf(KatachiTestTags.field(name)).performTextInput("User")
        waitForIdle()
    }

    private fun ComposeUiTest.press(tag: String, key: Key) {
        inputOf(tag).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.candidateShown(value: String): Boolean =
        onAllNodes(hasTestTag(KatachiTestTags.candidate(feature, value))).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `下キーで候補が開き打った文字の前方一致で絞られ候補を押すと値が入って閉じる`() = runComposeUiTest {
        val host = Host(loaded())
        openForm(host)
        inputOf(featureTag).requestFocus()
        press(featureTag, Key.DirectionDown)
        assertTrue(listOf("home", "settings", "profile").all { candidateShown(it) })

        inputOf(featureTag).performTextInput("SE")
        waitForIdle()
        assertTrue(candidateShown("settings"))
        assertFalse(candidateShown("home"))

        onNodeWithTag(KatachiTestTags.candidate(feature, "settings")).performClick()
        waitForIdle()
        assertEquals("settings", host.state.form.inputOf(feature))
        assertFalse(candidateShown("settings"))
        assertFalse(host.generated)
    }

    @Test
    fun `下で強調を動かして上で戻した候補をEnterで入れて閉じ生成はしない`() = runComposeUiTest {
        val host = Host(loaded())
        openForm(host)
        inputOf(featureTag).requestFocus()
        // open, home, settings, profile, and back up to settings
        listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionDown, Key.DirectionDown, Key.DirectionUp).forEach { press(featureTag, it) }
        press(featureTag, Key.Enter)

        assertEquals("settings", host.state.form.inputOf(feature))
        assertFalse(candidateShown("home"))
        assertFalse("開いている間のEnterは生成しない", host.generated)
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsEnabled()
    }

    @Test
    fun `開いている間のEscはポップアップだけ閉じフォーカスは欄に残り閉じた後のEscで行へ戻る`() = runComposeUiTest {
        val host = Host(loaded())
        openForm(host)
        inputOf(featureTag).requestFocus()
        press(featureTag, Key.DirectionDown)
        assertTrue(candidateShown("home"))

        press(featureTag, Key.Escape)
        assertFalse(candidateShown("home"))
        inputOf(featureTag).assertIsFocused()
        onNodeWithTag(KatachiTestTags.check(viewModel)).assertIsNotFocused()

        press(featureTag, Key.Escape)
        onNodeWithTag(KatachiTestTags.check(viewModel)).assertIsFocused()
    }

    @Test
    fun `閉じているEnterは普通の欄と同じく生成せず閉じたCmdEnterは生成し開いている間は生成しない`() = runComposeUiTest {
        val host = Host(loaded())
        openForm(host)
        inputOf(featureTag).performTextInput("home")
        waitForIdle()

        press(featureTag, Key.DirectionDown)
        inputOf(featureTag).performKeyInput { keyDown(Key.MetaLeft); pressKey(Key.Enter); keyUp(Key.MetaLeft) }
        waitForIdle()
        assertFalse("開いている間のCmd+Enterは生成しない", host.generated)
        assertFalse(candidateShown("home"))

        press(featureTag, Key.Enter)
        assertFalse("閉じているEnterは普通の欄と同じく生成しない", host.generated)

        inputOf(featureTag).performKeyInput { keyDown(Key.MetaLeft); pressKey(Key.Enter); keyUp(Key.MetaLeft) }
        waitForIdle()
        assertTrue("閉じているCmd+Enterは生成する", host.generated)
    }

    @Test
    fun `候補に無い値は新しいディレクトリの注意が出て候補が切れていると出ず候補の末尾に残り件数が出る`() = runComposeUiTest {
        val host = Host(loaded(CandidateList(listOf("home", "settings"), hiddenCount = 3, directoryPattern = "feature/{value}")))
        openForm(host)
        inputOf(featureTag).requestFocus()
        press(featureTag, Key.DirectionDown)
        onNodeWithTag(KatachiTestTags.candidateMore(feature)).assertExists()
        press(featureTag, Key.Escape)

        inputOf(featureTag).performTextInput("profile")
        waitForIdle()
        onNodeWithTag(KatachiTestTags.newDirectoryNote(feature)).assertDoesNotExist()
    }

    @Test
    fun `候補が全部見えているとき候補に無い値は新しいディレクトリの注意を出し候補の値では消える`() = runComposeUiTest {
        val host = Host(loaded())
        openForm(host)
        inputOf(featureTag).performTextInput("fresh")
        waitForIdle()
        onNodeWithTag(KatachiTestTags.newDirectoryNote(feature)).assertExists()

        inputOf(featureTag).performTextReplacement("home")
        waitForIdle()
        onNodeWithTag(KatachiTestTags.newDirectoryNote(feature)).assertDoesNotExist()
    }
}
