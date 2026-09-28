package me.tbsten.katachi.intellij.uitest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.data.generate.templateArgsOf
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.TemplateId
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
import org.junit.Test

/**
 * The capture fields of `contract/json/capture.json`, operated through the real Composables: what
 * is typed into them reaches the form, and from there the `--arg`s of the run.
 */
@OptIn(ExperimentalTestApi::class)
class CaptureInteractionTest {
    private val arch = module(":arch-a")
    private val viewModel = TemplateId(arch.id, "feature/ViewModel")
    private val screen = TemplateId(arch.id, "feature/Screen")
    private val feature = FieldId(viewModel, "feature")
    private val name = FieldId(viewModel, "name")

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
        snapshots = listOf(DescriptionSnapshot(arch, "0.3.0", ContractFixtures.templates("capture"), ScenarioHarness.NOW)),
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

    private fun Host.argsOf(id: TemplateId): List<Pair<String, String>> {
        val detail = state.rows.single { it.id == id }.template.detail ?: throw AssertionError("no detail")
        return templateArgsOf(id.roleName, detail, state.form.inputsOf(id), state.form.onExisting)
    }

    @Test
    fun `captureの欄は補足つきで出て切り替えは無く打った値が届く`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(KatachiTestTags.check(viewModel)).performClick()
        waitForIdle()

        onNodeWithTag(KatachiTestTags.hint(feature)).assertTextIs("生成先 feature/<feature>/src/*ViewModel.kt の <feature> に入るディレクトリ名")
        onNodeWithTag(KatachiTestTags.multiline(feature)).assertDoesNotExist()
        onNodeWithTag(KatachiTestTags.multiline(name)).assertExists()
        onNodeWithTag(KatachiTestTags.field(feature)).performTextInput("home")
        waitForIdle()
        assertEquals("home", host.state.form.inputOf(feature))
    }

    @Test
    fun `区切りを打つとエラーが出て直すと消え両方埋めると生成が押せてcaptureが--argに入る`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(KatachiTestTags.check(viewModel)).performClick()
        waitForIdle()
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsNotEnabled()

        onNodeWithTag(KatachiTestTags.field(feature)).performTextInput("home/list")
        waitForIdle()
        onNodeWithText("1階層の名前にしてください（/ と \\ は使えません）").assertExists()
        onNodeWithTag(KatachiTestTags.field(feature)).performTextReplacement("home")
        waitForIdle()
        onNodeWithText("1階層の名前にしてください（/ と \\ は使えません）").assertDoesNotExist()
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsNotEnabled()

        onNodeWithTag(KatachiTestTags.field(name)).performTextInput("User")
        waitForIdle()
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(KatachiIntent.Generate, host.sent.last())
        assertEquals(
            listOf("roleName" to "feature/ViewModel", "onExisting" to "fail", "feature" to "home", "name" to "User"),
            host.argsOf(viewModel),
        )
    }

    @Test
    fun `パスとモジュールの同名のcaptureは片方に打つともう一方の欄にも入る`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(KatachiTestTags.check(viewModel)).performClick()
        onNodeWithTag(KatachiTestTags.check(screen)).performClick()
        waitForIdle()
        onNodeWithTag(KatachiTestTags.field(feature)).performTextInput("home")
        waitForIdle()

        onNodeWithTag(KatachiTestTags.field(FieldId(screen, "feature"))).assertEditableTextIs("home")
        onNodeWithTag(KatachiTestTags.hint(FieldId(screen, "feature"))).assertTextIs("モジュール :feature:<feature> の <feature> に入る、既存のモジュール名")
        assertEquals("feature" to "home", host.argsOf(screen)[2])
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertTextIs(expected: String) =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(expected))))

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertEditableTextIs(expected: String) =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))
