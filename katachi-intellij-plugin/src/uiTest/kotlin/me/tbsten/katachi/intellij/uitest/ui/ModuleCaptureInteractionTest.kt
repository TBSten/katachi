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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
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
 * sample/android's FeatureComponent (`":feature:${capture("feature")}".module { }`) in the tool
 * window, operated through the real Composables over the sample's real JSON
 * (`sample-android-with-captures`, which says where each existing module puts the file): typing an
 * existing module into `feature` shows where the file goes and lets [Generate] be pressed; a module
 * that does not exist says which ones do and keeps it disabled.
 */
@OptIn(ExperimentalTestApi::class)
class ModuleCaptureInteractionTest {
    private val arch = module(":architecture-test")
    private val component = TemplateId(arch.id, "feature.FeatureComponent")
    private val feature = FieldId(component, "feature")
    private val name = FieldId(component, "name")

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
        snapshots = listOf(DescriptionSnapshot(arch, "0.3.0", ContractFixtures.templates("sample-android-with-captures"), ScenarioHarness.NOW)),
    )

    private fun ComposeUiTest.show(host: Host) {
        setContent {
            IntUiTheme {
                Box(Modifier.requiredSize(480.dp, 720.dp).background(JewelTheme.globalColors.panelBackground)) {
                    KatachiToolWindowContent(uiStateOf(host.state, JapaneseKatachiStrings, ScenarioHarness.NOW), host::send)
                }
            }
        }
    }

    private fun Host.args(): List<Pair<String, String>> {
        val detail = state.rows.single { it.id == component }.template.detail ?: throw AssertionError("no detail")
        return templateArgsOf(component.template, detail, state.form.inputsOf(component), state.form.onExisting)
    }

    @Test
    fun `featureに今あるモジュールを入れると生成先が見本に出て名前を入れると生成が押せる`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(KatachiTestTags.check(component)).performClick()
        waitForIdle()
        onNodeWithTag(KatachiTestTags.hint(feature)).assertTextIs("モジュール :feature:<feature> の <feature> に入る、既存のモジュール名")
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsNotEnabled()

        onNodeWithTag(KatachiTestTags.field(feature)).performTextInput("home")
        waitForIdle()
        onNodeWithTag(KatachiTestTags.hint(feature))
            .assertTextIs("生成先 feature/home/src/main/kotlin/com/example/sample/feature/home/component/Home<name>.kt")
        // The name is still empty.
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsNotEnabled()

        onNodeWithTag(KatachiTestTags.field(name)).performTextInput("UserCard")
        waitForIdle()
        onNodeWithTag(KatachiTestTags.hint(feature))
            .assertTextIs("生成先 feature/home/src/main/kotlin/com/example/sample/feature/home/component/HomeUserCard.kt")
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsEnabled().performClick()
        waitForIdle()

        assertEquals(KatachiIntent.Generate, host.sent.last())
        assertEquals(
            listOf("template" to "feature.FeatureComponent", "onExisting" to "fail", "feature" to "home", "name" to "UserCard"),
            host.args(),
        )
    }

    @Test
    fun `無いモジュールを入れると今あるモジュールを示して押せず直すと生成先が変わり消すとまた押せない`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        onNodeWithTag(KatachiTestTags.check(component)).performClick()
        waitForIdle()
        onNodeWithTag(KatachiTestTags.field(name)).performTextInput("UserCard")
        onNodeWithTag(KatachiTestTags.field(feature)).performTextInput("hoem")
        waitForIdle()

        onNodeWithText("今あるモジュールから選んでください（home, settings）").assertExists()
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsNotEnabled()

        onNodeWithTag(KatachiTestTags.field(feature)).performTextReplacement("settings")
        waitForIdle()
        onNodeWithText("今あるモジュールから選んでください（home, settings）").assertDoesNotExist()
        onNodeWithTag(KatachiTestTags.hint(feature))
            .assertTextIs("生成先 feature/settings/src/main/kotlin/com/example/sample/feature/settings/component/SettingsUserCard.kt")
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsEnabled()

        onNodeWithTag(KatachiTestTags.field(feature)).performTextClearance()
        waitForIdle()
        onNodeWithTag(KatachiTestTags.hint(feature)).assertTextIs("モジュール :feature:<feature> の <feature> に入る、既存のモジュール名")
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsNotEnabled()
    }
}

private fun SemanticsNodeInteraction.assertTextIs(expected: String) =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(expected))))
