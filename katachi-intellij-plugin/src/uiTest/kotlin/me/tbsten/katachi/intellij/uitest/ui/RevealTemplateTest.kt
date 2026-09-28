package me.tbsten.katachi.intellij.uitest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.uiStateOf
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability
import me.tbsten.katachi.intellij.presentation.entry.EntrySettings
import me.tbsten.katachi.intellij.presentation.entry.FileContentState
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import me.tbsten.katachi.intellij.presentation.entry.NotificationInput
import me.tbsten.katachi.intellij.presentation.entry.decideNotification
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.cast
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.patternTemplate
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import me.tbsten.katachi.intellij.uitest.pbt.contractJsonOf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [View template] of an editor notification, drawn (C2, design "検証の計画" ジャンプ・強調): the
 * three patterns of the plan, headless. The real ViewModel runs over [ScenarioHarness]'s fakes on a
 * [ManualDispatcher], the notification's decision is the pure one E1 calls, and the real
 * Composables draw the list. "In view" is measured: the highlighted header's bounds lie inside the
 * list's viewport, and no module band pinned to the top covers them.
 */
@OptIn(ExperimentalTestApi::class)
class RevealTemplateTest {
    private val dispatcher = ManualDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val harness = ScenarioHarness(scope, ioDispatcher = dispatcher)
    private val arch = harness.arch

    /** Enough rows in groups to run far past a 400dp viewport. */
    private fun fillers(prefix: String, count: Int): List<TemplateModel> =
        (0 until count).map { patternTemplate("$prefix${it / 6}.Filler$it", "$prefix/$it/\${name}.kt") }

    /** Declared first, so first in the index order, though its title sorts after the other's. */
    private val anyKotlin = patternTemplate("data.ZAnyKotlin", "data/\${name}.kt")
    private val repository = patternTemplate("data.ARepository", "data/\${name}Repository.kt")

    /** The two templates that both fit `data/UserRepository.kt`, far down the list. */
    private val catalog = fillers("g", 36) + anyKotlin + repository + fillers("h", 6)

    private fun idOf(template: TemplateModel, module: KatachiModule = arch) = TemplateId(module.id, template.template)

    @After
    fun tearDown() = scope.cancel()

    private fun dispatch(vararg intents: KatachiIntent) {
        intents.forEach(harness.vm::dispatch)
        dispatcher.runAll()
    }

    /** The tool window's content as the factory creates it: the state collected, `Opened` sent. */
    private fun ComposeUiTest.openToolWindow() {
        setContent {
            IntUiTheme {
                Box(Modifier.requiredSize(300.dp, 400.dp).background(JewelTheme.globalColors.panelBackground)) {
                    val state by harness.vm.state.collectAsState()
                    KatachiToolWindowContent(uiStateOf(state, JapaneseKatachiStrings, ScenarioHarness.NOW), harness.vm::dispatch)
                }
            }
        }
        dispatch(KatachiIntent.Opened)
        waitForIdle()
    }

    /** What [View template] on `file`'s notification reveals: the first match of the index order (decision 9). */
    private fun viewTemplateTargetOf(file: String): Pair<TemplateId, Int> {
        val index = TemplatePlacementIndex.build(harness.state.snapshots) { it.linkedRootPath }
        val path = ROOT.resolve(file)
        val input = NotificationInput(
            file = path,
            content = FileContentState.HasContent,
            availability = EntryAvailability.Ready,
            matches = index.matchesForFile(path),
            settings = EntrySettings(),
            memory = EditorNotificationMemory.EMPTY,
            ledgerEntry = null,
            commentable = true,
        )
        val matches = decideNotification(input).cast<NotificationDecision.ViewTemplate>().matches
        return matches.first().id to matches.size
    }

    private fun ComposeUiTest.viewport(): Rect = onNodeWithTag(KatachiTestTags.LIST).fetchSemanticsNode().boundsInRoot

    /** Exactly one highlighted header, [title]'s, inside the viewport and under no module band. */
    private fun ComposeUiTest.assertHighlightedInView(title: String, bandTitles: List<String> = emptyList()) {
        onAllNodesWithTag(KatachiTestTags.HIGHLIGHTED).assertCountEquals(1)
        val node = onNodeWithTag(KatachiTestTags.HIGHLIGHTED)
        node.assert(hasAnyDescendant(hasText(title)))
        val row = node.fetchSemanticsNode().unclippedBounds()
        val list = viewport()
        assertTrue("the highlighted row $row is not inside the viewport $list", row.top >= list.top && row.bottom <= list.bottom)
        for (band in bandTitles) {
            onAllNodesWithText(band).fetchSemanticsNodes().map { it.unclippedBounds() }.forEach { bounds ->
                val covers = bounds.bottom > row.top && bounds.top < row.bottom
                assertFalse("the module band \"$band\" at $bounds covers the highlighted row $row", covers)
            }
        }
    }

    private fun ComposeUiTest.isInView(title: String): Boolean {
        val list = viewport()
        return onAllNodesWithText(title).fetchSemanticsNodes().map { it.unclippedBounds() }.any { it.top >= list.top && it.bottom <= list.bottom }
    }

    /** Where the node is, not clipped by the scrolling list as `boundsInRoot` is. */
    private fun SemanticsNode.unclippedBounds(): Rect = Rect(positionInRoot, size.toSize())

    // covers: 検証の計画 ジャンプ・強調 (a)、10章 9
    @Test
    fun `複数のテンプレートが当たるファイルから見ると索引の並びで最初のテンプレートだけが強調され見える位置に来る`() = runComposeUiTest {
        harness.loadJson = contractJsonOf(catalog)
        openToolWindow()
        assertFalse(isInView(anyKotlin.summary.title))

        val (target, matchCount) = viewTemplateTargetOf("data/UserRepository.kt")
        assertEquals(2, matchCount)
        assertEquals(idOf(anyKotlin), target)
        dispatch(KatachiIntent.RevealTemplate(target))
        waitForIdle()

        assertHighlightedInView(anyKotlin.summary.title)
        onNodeWithTag(KatachiTestTags.HIGHLIGHTED).assert(!hasAnyDescendant(hasText(repository.summary.title)))
    }

    // covers: 検証の計画 ジャンプ・強調 (b)
    @Test
    fun `ツールウィンドウが無いまま通知から見てもあとで開いたツールウィンドウでその行が強調され見える位置に来る`() = runComposeUiTest {
        harness.loadJson = contractJsonOf(catalog)
        // The notification loaded the list without the tool window (issue 11), then [View template].
        dispatch(KatachiIntent.EnsureLoaded)
        val (target, _) = viewTemplateTargetOf("data/UserRepository.kt")
        dispatch(KatachiIntent.RevealTemplate(target))

        openToolWindow()

        assertHighlightedInView(anyKotlin.summary.title)
    }

    // covers: 検証の計画 ジャンプ・強調 (b)
    @Test
    fun `一覧を読み込む前に強調を頼んでもツールウィンドウを開いて読み込んだ後にその行が見える位置に来る`() = runComposeUiTest {
        harness.loadJson = contractJsonOf(catalog)
        dispatch(KatachiIntent.RevealTemplate(idOf(repository)))

        openToolWindow()

        assertHighlightedInView(repository.summary.title)
    }

    // covers: 検証の計画 ジャンプ・強調 (c)
    @Test
    fun `スクロールの外にある行を下へ上へと続けて頼むとどちらもビューポートの内側に来て固定のモジュール見出しに隠れない`() = runComposeUiTest {
        val other = module(":arch-b")
        harness.addModule(other, contractJsonOf(fillers("k", 30)))
        harness.loadJson = contractJsonOf(catalog)
        openToolWindow()
        val bands = listOf(arch.gradlePath, other.gradlePath)
        val rows = fillers("g", 36)

        // Down to a row below the list, then up to one a few rows above the top edge: the list
        // scrolls only as far as it must, and the first module's band is pinned over the top then.
        dispatch(KatachiIntent.RevealTemplate(idOf(rows[30])))
        waitForIdle()
        assertHighlightedInView(rows[30].summary.title, bands)
        dispatch(KatachiIntent.RevealTemplate(idOf(rows[22])))
        waitForIdle()
        assertHighlightedInView(rows[22].summary.title, bands)

        val last = fillers("k", 30).last()
        dispatch(KatachiIntent.RevealTemplate(idOf(last, other)))
        waitForIdle()
        assertHighlightedInView(last.summary.title, bands)

        // Back up to the middle of the first module, far above the list.
        val first = rows[20]
        dispatch(KatachiIntent.RevealTemplate(idOf(first)))
        waitForIdle()
        assertFalse(isInView(last.summary.title))
        assertHighlightedInView(first.summary.title, bands)
    }

    // covers: 検証の計画 ジャンプ・強調 (c)
    @Test
    fun `畳んだモジュールと検索で隠れた行を頼むと開いて検索が空になりその行が見える位置に来る`() = runComposeUiTest {
        val other = module(":arch-b")
        harness.addModule(other, contractJsonOf(fillers("k", 30)))
        harness.loadJson = contractJsonOf(catalog)
        openToolWindow()
        dispatch(KatachiIntent.ToggleModule(other.id), KatachiIntent.Search("g1."))
        waitForIdle()

        val target = fillers("k", 30)[20]
        dispatch(KatachiIntent.RevealTemplate(idOf(target, other)))
        waitForIdle()

        assertEquals("", harness.state.searchQuery)
        assertHighlightedInView(target.summary.title, listOf(arch.gradlePath, other.gradlePath))
    }
}
