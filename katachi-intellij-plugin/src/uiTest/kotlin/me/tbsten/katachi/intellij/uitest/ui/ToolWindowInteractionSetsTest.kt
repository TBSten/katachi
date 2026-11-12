package me.tbsten.katachi.intellij.uitest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.BodyUi
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.ListItemUi
import me.tbsten.katachi.intellij.presentation.RowBodyUi
import me.tbsten.katachi.intellij.presentation.uiStateOf
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A template set's row operated the way a user does (template-set plan 3.6): the real Composables over the real ViewModel on
 * [ScenarioHarness]'s fakes, the definition katachi wrote for `synthetic-template-set`. What a generation sends is read from
 * the fake Gradle (`harness.katachi.runs`), what it wrote from the fake file system.
 */
@OptIn(ExperimentalTestApi::class)
class ToolWindowInteractionSetsTest {
    private val dispatcher = ManualDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val harness = ScenarioHarness(scope, ioDispatcher = dispatcher).also { it.loadJson = ContractFixtures.json("synthetic-template-set") }
    private val arch = harness.arch

    private fun idOf(template: String) = TemplateId(arch.id, template)
    private val starter = idOf("Starter")
    private val newFeature = idOf("feature.NewFeature")
    private val broken = idOf("feature.Broken")
    private val screen = idOf("feature.FeatureScreen")
    private val viewModel = idOf("feature.FeatureViewModel")
    private val logger = idOf("shared.Logger")

    @After
    fun tearDown() = scope.cancel()

    /** Lets the composition and the ViewModel's coroutines (parked on the manual dispatcher) run until neither has more to do. */
    private fun ComposeUiTest.settle() {
        repeat(3) {
            waitForIdle()
            dispatcher.runAll()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.openToolWindow() {
        setContent {
            IntUiTheme {
                Box(Modifier.requiredSize(420.dp, 740.dp).background(JewelTheme.globalColors.panelBackground)) {
                    val state by harness.vm.state.collectAsState()
                    KatachiToolWindowContent(uiStateOf(state, JapaneseKatachiStrings, ScenarioHarness.NOW), harness.vm::dispatch)
                }
            }
        }
        harness.vm.dispatch(KatachiIntent.Opened)
        settle()
    }

    private fun ComposeUiTest.check(id: TemplateId) {
        onNodeWithTag(KatachiTestTags.check(id)).performClick()
        settle()
    }

    private fun ComposeUiTest.type(id: TemplateId, parameter: String, text: String) {
        inputOf(KatachiTestTags.field(FieldId(id, parameter))).performTextInput(text)
        settle()
    }

    private fun ComposeUiTest.fieldShown(id: TemplateId, parameter: String): Boolean =
        onAllNodes(hasTestTag(KatachiTestTags.field(FieldId(id, parameter)))).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.generate() {
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsEnabled().performClick()
        settle()
    }

    private fun shownRowIds(): List<TemplateId> =
        ((uiStateOf(harness.state, JapaneseKatachiStrings, ScenarioHarness.NOW).body as BodyUi.Listing).list.items)
            .mapNotNull { (it as? ListItemUi.Row)?.row?.id }

    @Test
    fun `組の行を押してチェックし組のパラメータだけを埋めて生成すると組の1回が組の指定とそのパラメータで届き結果に組の1行が出る`() = runComposeUiTest {
        openToolWindow()
        onNode(hasTestTag(KatachiTestTags.setMembers(newFeature)), useUnmergedTree = true).assertExists()
        onNode(hasTestTag(KatachiTestTags.setBadge(newFeature)), useUnmergedTree = true).assertExists()
        check(newFeature)
        onNodeWithTag(KatachiTestTags.check(newFeature)).assertIsOn()

        // The form holds the set's parameters (feature, name, withState), not the members' own (Logger's tag).
        assertTrue(fieldShown(newFeature, "feature") && fieldShown(newFeature, "name") && fieldShown(newFeature, "withState"))
        assertTrue("メンバのパラメータは組のフォームに出ない", !fieldShown(newFeature, "tag"))
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsNotEnabled()

        type(newFeature, "feature", "home")
        type(newFeature, "name", "Home")
        generate()

        assertEquals(1, harness.katachi.runs.size)
        val args = harness.katachi.runs.single()
        assertEquals("feature.NewFeature", args["template"])
        assertEquals("fail", args["onExisting"])
        assertEquals("home", args["feature"])
        assertEquals("Home", args["name"])
        assertEquals(setOf("template", "onExisting", "feature", "name"), args.keys - "withState")

        val rows = (uiStateOf(harness.state, JapaneseKatachiStrings, ScenarioHarness.NOW).body as BodyUi.Listing).list.items
            .filterIsInstance<ListItemUi.Row>().map { it.row }
        val results = rows.filter { it.body is RowBodyUi.Result }
        assertEquals("結果は組の1行だけ", listOf(newFeature), results.map { it.id })
        assertTrue("結果の行に書いたファイルが出る", (results.single().body as RowBodyUi.Result).result.files.isNotEmpty())
        assertTrue(harness.state.generation is GenerationState.Finished)
    }

    @Test
    fun `組の行と同じモジュールのテンプレート2つをチェックすると同名の欄は連動し生成は組だけとテンプレート2つのカンマ区切りの2回になる`() = runComposeUiTest {
        openToolWindow()
        val order = shownRowIds()
        check(newFeature)
        check(screen)
        check(viewModel)

        // Same name and same kind link across a set and a template: the set's withState with the template's.
        val setWithState = KatachiTestTags.field(FieldId(newFeature, "withState"))
        val templateWithState = KatachiTestTags.field(FieldId(viewModel, "withState"))
        onNodeWithTag(setWithState).performClick()
        settle()
        val followed = harness.state.form.inputOf(FieldId(newFeature, "withState"))
        assertTrue("クリックで withState に値が入る", followed != null)
        assertEquals(followed, harness.state.form.inputOf(FieldId(viewModel, "withState")))
        if (followed == "true") onNodeWithTag(templateWithState).assertIsOn() else onNodeWithTag(templateWithState).assertIsOff()

        type(newFeature, "feature", "home")
        type(newFeature, "name", "Home")
        // The two templates' `name` captures link with each other.
        type(screen, "name", "User")
        inputOf(KatachiTestTags.field(FieldId(viewModel, "name"))).assertEditableTextIs("User")
        generate()

        // Runs follow the list: the module's plain templates stand where the first of them does, the set where it does.
        val plain = "feature.FeatureScreen,feature.FeatureViewModel"
        val expected = if (order.indexOf(newFeature) < order.indexOf(screen)) listOf("feature.NewFeature", plain) else listOf(plain, "feature.NewFeature")
        assertEquals(expected, harness.katachi.runs.map { it.getValue("template") })
        val setRun = harness.katachi.runs.single { it["template"] == "feature.NewFeature" }
        assertEquals("home", setRun["feature"])
        assertEquals("Home", setRun["name"])
        assertEquals("User", harness.katachi.runs.single { it["template"] == plain }["name"])
    }

    @Test
    fun `組の行の必須の欄が空のうちは生成が押せず埋めると押せる`() = runComposeUiTest {
        openToolWindow()
        check(starter)
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsNotEnabled()

        type(starter, "name", "Home")

        onNodeWithTag(KatachiTestTags.GENERATE).assertIsEnabled()
    }

    @Test
    fun `検索欄にメンバの題を打つと組の行が残りほかの行は消え空にすると全部戻る`() = runComposeUiTest {
        openToolWindow()
        val all = shownRowIds()

        onNodeWithTag(KatachiTestTags.SEARCH).performTextInput("FeatureViewModel")
        settle()
        val shown = shownRowIds()
        assertTrue("組の行は題にメンバの名が無くても残る", starter in shown && newFeature in shown)
        assertTrue(logger !in shown && broken !in shown)
        onNode(hasTestTag(KatachiTestTags.setBadge(starter)), useUnmergedTree = true).assertExists()
        assertTrue("テンプレートの行には組のバッジが無い", viewModel in shown)
        onAllNodes(hasTestTag(KatachiTestTags.setBadge(viewModel)), useUnmergedTree = true).assertCountEquals(0)

        onNodeWithTag(KatachiTestTags.SEARCH).performKeyInput { pressKey(Key.Escape) }
        settle()
        assertEquals(all, shownRowIds())
    }

    @Test
    fun `衝突した組の行はチェックできず理由が表示される`() = runComposeUiTest {
        openToolWindow()
        onNodeWithTag(KatachiTestTags.check(broken)).assertIsNotEnabled()

        onNodeWithTag(KatachiTestTags.check(broken)).performClick()
        settle()
        onNodeWithTag(KatachiTestTags.check(broken)).assertIsOff()
        assertTrue(broken !in harness.state.form.selected)

        onNodeWithTag(KatachiTestTags.moreMenu(broken)).performClick()
        settle()
        onNodeWithText(JapaneseKatachiStrings.showCause).performClick()
        settle()
        onNodeWithText("[FAILED] misc/Broken").assertExists()
    }

    @Test
    fun `キーボードだけで組の行へ移りスペースでチェックし左で畳み右で開く`() = runComposeUiTest {
        openToolWindow()
        val order = shownRowIds()
        onNodeWithTag(KatachiTestTags.SEARCH).requestFocus()
        var focused = KatachiTestTags.SEARCH
        for (id in order.take(order.indexOf(starter) + 1)) {
            onNodeWithTag(focused).performKeyInput { pressKey(Key.DirectionDown) }
            settle()
            focused = KatachiTestTags.check(id)
        }
        onNodeWithTag(KatachiTestTags.check(starter)).assertIsFocused()

        onNodeWithTag(KatachiTestTags.check(starter)).performKeyInput { pressKey(Key.Spacebar) }
        settle()
        onNodeWithTag(KatachiTestTags.check(starter)).assertIsOn()
        assertTrue(fieldShown(starter, "name"))

        onNodeWithTag(KatachiTestTags.check(starter)).performKeyInput { pressKey(Key.DirectionLeft) }
        settle()
        assertTrue("左で畳む", !fieldShown(starter, "name"))
        onNodeWithTag(KatachiTestTags.check(starter)).performKeyInput { pressKey(Key.DirectionRight) }
        settle()
        assertTrue("右で開く", fieldShown(starter, "name"))
        onNode(hasTestTag(KatachiTestTags.setBadge(starter)), useUnmergedTree = true).assert(hasText("set"))
    }
}

private fun SemanticsNodeInteraction.assertEditableTextIs(expected: String) =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))
