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
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.CandidateList
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.IdeEffects
import me.tbsten.katachi.intellij.presentation.IdeIntentHandler
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.ScreenPhase
import me.tbsten.katachi.intellij.presentation.applyFormIntent
import me.tbsten.katachi.intellij.presentation.uiStateOf
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeIdeEffects
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * The tool window operated the way a user does, one column of operations per test (plan ide-review-screens 4.3, the list side):
 * a candidate picked with the mouse and with the keys, a new value, Esc in its order, a module band moved with the keys, Esc in
 * the search field, and the row menu's "Open Definition". Every test goes through the real Composables.
 */
@OptIn(ExperimentalTestApi::class)
class ToolWindowScenarioInteractionTest {
    private val arch = module(":arch-a")
    private val viewModel = TemplateId(arch.id, "feature.ViewModel")
    private val feature = FieldId(viewModel, "feature")
    private val name = FieldId(viewModel, "name")
    private val featureTag = KatachiTestTags.field(feature)

    private class Host(initial: KatachiScreenState, private val effects: IdeEffects = FakeIdeEffects()) {
        var state by mutableStateOf(initial)
        val sent = mutableListOf<KatachiIntent>()

        fun send(intent: KatachiIntent) {
            sent += intent
            val next = applyFormIntent(state, intent)
            if (next != null) {
                state = next
                return
            }
            // What the form does not settle is the IDE's: hand it to the handler the plugin uses.
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                IdeIntentHandler(scope, FakeGradleTaskRunner(FakeFileSystem()) { _, _ -> FakeRun() }, FakeFileSystem(), effects, { state }, {}).handle(intent)
            } finally {
                scope.cancel()
            }
        }

        val generated: Boolean get() = sent.any { it == KatachiIntent.Generate }
    }

    private class RecordingEffects(private val base: FakeIdeEffects = FakeIdeEffects()) : IdeEffects by base {
        val opened: MutableList<Triple<String, String, DeclarationSiteRef?>> = Collections.synchronizedList(mutableListOf())

        override suspend fun openDeclaration(module: KatachiModule, roleName: String, site: DeclarationSiteRef?) {
            opened += Triple(module.gradlePath, roleName, site)
        }
    }

    private fun loaded(
        modules: List<KatachiModule> = listOf(arch),
        candidates: CandidateList = CandidateList(listOf("home", "settings", "profile"), directoryPattern = "feature/{value}"),
        definitionOf: (KatachiModule) -> Boolean = { false },
    ) = KatachiScreenState(
        phase = ScreenPhase.Ready,
        modules = modules,
        snapshots = modules.map { module ->
            val definition = definitionModel(roleModel("feature.ViewModel", layoutEntry("a.kt"))).takeIf { definitionOf(module) }
            DescriptionSnapshot(module, "0.3.0", ContractFixtures.templates("capture"), ScenarioHarness.NOW, definition)
        },
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
        onNodeWithTag(tag).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.pressInput(tag: String, key: Key) {
        inputOf(tag).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.candidateShown(value: String): Boolean =
        onAllNodes(hasTestTag(KatachiTestTags.candidate(feature, value))).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `行をチェックして三角を押し候補をクリックすると欄に入りポップアップが閉じ生成が押せる`() = runComposeUiTest {
        val host = Host(loaded())
        openForm(host)
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsNotEnabled()

        clickChevron(featureTag)
        assertTrue(listOf("home", "settings", "profile").all { candidateShown(it) })
        onNodeWithTag(KatachiTestTags.candidate(feature, "settings")).performClick()
        waitForIdle()

        assertEquals("settings", host.state.form.inputOf(feature))
        assertFalse(candidateShown("settings"))
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsEnabled()
    }

    @Test
    fun `se と打つとポップアップは settings だけになりそのまま Enter を押すと settings が入る`() = runComposeUiTest {
        val host = Host(loaded())
        openForm(host)
        inputOf(featureTag).requestFocus()
        pressInput(featureTag, Key.DirectionDown)
        inputOf(featureTag).performTextInput("se")
        waitForIdle()
        assertTrue(candidateShown("settings"))
        assertFalse(candidateShown("home"))
        assertFalse(candidateShown("profile"))

        // The open popup highlights the first candidate it lists: with the one left, Enter takes it.
        pressInput(featureTag, Key.Enter)

        assertEquals("settings", host.state.form.inputOf(feature))
        assertFalse(candidateShown("settings"))
        assertFalse("開いている間のEnterは生成しない", host.generated)
    }

    @Test
    fun `候補に無い profile を打つと新しいディレクトリの注意が出て生成が押せる`() = runComposeUiTest {
        val host = Host(loaded(candidates = CandidateList(listOf("home", "settings"), directoryPattern = "feature/{value}")))
        openForm(host)
        inputOf(featureTag).performTextInput("profile")
        waitForIdle()

        onNodeWithTag(KatachiTestTags.newDirectoryNote(feature))
            .assert(hasText("feature/profile は新しいディレクトリになります"))
        onNodeWithTag(KatachiTestTags.GENERATE).assertIsEnabled()
    }

    @Test
    fun `ポップアップを開いてEscで閉じフォーカスは欄に残りもう一度Escでフォーカスは行に移る`() = runComposeUiTest {
        val host = Host(loaded())
        openForm(host)
        inputOf(featureTag).requestFocus()
        pressInput(featureTag, Key.DirectionDown)
        assertTrue(candidateShown("home"))

        pressInput(featureTag, Key.Escape)
        assertFalse(candidateShown("home"))
        inputOf(featureTag).assertIsFocused()

        pressInput(featureTag, Key.Escape)
        onNodeWithTag(KatachiTestTags.check(viewModel)).assertIsFocused()
        assertFalse(host.generated)
    }

    @Test
    fun `定義が2つあるとき検索欄から下で帯へ入り左で畳み下で次の帯へ移りEnterで開く`() = runComposeUiTest {
        val archB = module(":arch-b")
        val host = Host(loaded(modules = listOf(arch, archB)))
        show(host)
        val bandA = KatachiTestTags.moduleBand(arch.id)
        val bandB = KatachiTestTags.moduleBand(archB.id)
        onNodeWithTag(KatachiTestTags.SEARCH).requestFocus()

        press(KatachiTestTags.SEARCH, Key.DirectionDown)
        onNodeWithTag(bandA).assertIsFocused()
        press(bandA, Key.DirectionLeft)
        assertTrue(arch.id in host.state.view.collapsedModules)

        press(bandA, Key.DirectionDown)
        onNodeWithTag(bandB).assertIsFocused()
        press(bandB, Key.DirectionLeft)
        assertTrue(archB.id in host.state.view.collapsedModules)

        press(bandB, Key.Enter)
        assertTrue("Enter で次の帯が開く", archB.id !in host.state.view.collapsedModules)
        assertTrue("先に畳んだ帯はそのまま", arch.id in host.state.view.collapsedModules)
        onNodeWithTag(bandB).assertIsFocused()
    }

    @Test
    fun `検索欄に Repo と打ってEscを押すと検索語が空になり全行が出る`() = runComposeUiTest {
        val host = Host(loaded())
        show(host)
        val row = KatachiTestTags.check(viewModel)
        onNodeWithTag(row).assertExists()

        onNodeWithTag(KatachiTestTags.SEARCH).performTextInput("Repo")
        waitForIdle()
        assertEquals("Repo", host.state.searchQuery)
        onNodeWithTag(row).assertDoesNotExist()

        press(KatachiTestTags.SEARCH, Key.Escape)
        assertEquals("", host.state.searchQuery)
        onNodeWithTag(KatachiTestTags.SEARCH).assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        onNodeWithTag(row).assertExists()
    }

    @Test
    fun `定義モデルがある行の三点から定義を開くと偽のIDEにRoleの宣言位置が届く`() = runComposeUiTest {
        val effects = RecordingEffects()
        val host = Host(loaded(definitionOf = { true }), effects)
        show(host)
        onNodeWithTag(KatachiTestTags.check(viewModel)).performClick()
        waitForIdle()

        onNodeWithTag(KatachiTestTags.moreMenu(viewModel)).performClick()
        waitForIdle()
        onNodeWithText(JapaneseKatachiStrings.openDefinition).performClick()
        waitForIdle()

        assertTrue(host.sent.contains(KatachiIntent.OpenDefinition(viewModel)))
        assertEquals(listOf(Triple(":arch-a", "feature.ViewModel", DeclarationSiteRef("ViewModelRole.kt", 1))), effects.opened.toList())
    }

    @Test
    fun `定義モデルが無い行の三点には定義を開くの項目が無い`() = runComposeUiTest {
        val effects = RecordingEffects()
        val host = Host(loaded(definitionOf = { false }), effects)
        show(host)
        onNodeWithTag(KatachiTestTags.check(viewModel)).performClick()
        waitForIdle()

        // The row has a menu (it holds "copy the command"), and it opens: only then does a missing item mean something.
        onNodeWithTag(KatachiTestTags.moreMenu(viewModel)).assertExists()
        onNodeWithTag(KatachiTestTags.moreMenu(viewModel)).performClick()
        waitForIdle()
        onNodeWithText(JapaneseKatachiStrings.copyCommand).assertExists()
        assertTrue(onAllNodes(hasText(JapaneseKatachiStrings.openDefinition)).fetchSemanticsNodes().isEmpty())
        assertNull(effects.opened.firstOrNull())
    }
}
