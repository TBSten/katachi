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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.data.placement.DirectoryLister
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.ModuleTemplate
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
import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogContent
import me.tbsten.katachi.intellij.uitest.dialog.DialogHost
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The candidate popup of a capture field, played as a sequence of the keys and clicks a user makes (ide-review-screens plan 4.2,
 * the operations OpenCandidates / PickCandidate / PressEscape and invariants 2, 4 and 5), in the tool window and in the
 * dialog, through the real Composables. After every step:
 *
 * - (4) Esc with the popup open closes only the popup: nothing else is told (no intent, no cancel) and the field keeps the focus.
 * - (5) Enter and Cmd+Enter with the popup open generate nothing, and leave it closed.
 * - (2) A candidate taken (Enter on the highlighted one, or a click) is the field's value, told as the same input typing it would tell.
 *
 * Sequences are drawn from `-Pkatachi.pbt.seed` (shorter than the screen machines': each step drives a real popup). A failure
 * prints the shortest sequence found by dropping steps one at a time.
 */
@OptIn(ExperimentalTestApi::class)
class CandidatePopupPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261018L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0

    private sealed interface PopupOp {
        data object Down : PopupOp
        data object Chevron : PopupOp
        data object Up : PopupOp
        data object Enter : PopupOp
        data object CmdEnter : PopupOp
        data object Esc : PopupOp
        data class Type(val text: String) : PopupOp
        data class Click(val index: Int) : PopupOp
    }

    private val typed = listOf("", "h", "ho", "SE", "zzz", "home", "Pro")

    private fun opsOf(random: Random, length: Int): List<PopupOp> = List(length) {
        when (random.nextInt(17)) {
            in 0..3 -> PopupOp.Down
            in 4..5 -> PopupOp.Chevron
            6 -> PopupOp.Up
            in 7..8 -> PopupOp.Enter
            9 -> PopupOp.CmdEnter
            in 10..11 -> PopupOp.Esc
            in 12..13 -> PopupOp.Type(typed[random.nextInt(typed.size)])
            else -> PopupOp.Click(random.nextInt(3))
        }
    }

    /** One screen the sequence is played on. */
    private interface Surface {
        val fieldTag: String

        /** The candidates in the order the popup lists them (the dialog reads them from the disk, which sorts them). */
        val listed: List<String>

        /** The test tag of the candidate [value] row of the popup. */
        fun candidateTag(value: String): String

        fun ComposeUiTest.show()
        fun value(): String

        /** Everything that reached the screen's owner (intents, calls): Esc and Enter of an open popup must not add to it. */
        fun told(): Int

        /** Generate requests so far. */
        fun generated(): Int

        /** Times the screen asked to be left (the dialog's cancel); the tool window has none. */
        fun left(): Int

        /** The inputs told for the field, in order, when the surface records them. */
        fun inputs(): List<String>?
    }

    private val candidates = listOf("home", "settings", "profile")

    /** What the steps reached, so that the checks are known to have been put to the test. */
    private val reached = sortedMapOf<String, Int>()

    private fun reach(key: String) {
        reached.merge(key, 1, Int::plus)
    }

    private inner class ToolWindowSurface : Surface {
        private val arch = module(":arch-a")
        private val viewModel = TemplateId(arch.id, "feature.ViewModel")
        private val feature = FieldId(viewModel, "feature")
        private var state by mutableStateOf(
            KatachiScreenState(
                phase = ScreenPhase.Ready,
                modules = listOf(arch),
                snapshots = listOf(DescriptionSnapshot(arch, "0.3.0", ContractFixtures.templates("capture"), ScenarioHarness.NOW)),
                captureCandidates = mapOf(feature to CandidateList(candidates, directoryPattern = "feature/{value}")),
            ),
        )
        private val sent = mutableListOf<KatachiIntent>()
        override val fieldTag = KatachiTestTags.field(feature)
        override val listed = candidates
        override fun candidateTag(value: String) = KatachiTestTags.candidate(feature, value)

        override fun ComposeUiTest.show() {
            setContent {
                IntUiTheme {
                    Box(Modifier.requiredSize(420.dp, 720.dp).background(JewelTheme.globalColors.panelBackground)) {
                        KatachiToolWindowContent(uiStateOf(state, JapaneseKatachiStrings, ScenarioHarness.NOW), ::send)
                    }
                }
            }
            onNodeWithTag(KatachiTestTags.check(viewModel)).performClick()
            waitForIdle()
            inputOf(KatachiTestTags.field(FieldId(viewModel, "name"))).performTextInput("User")
            waitForIdle()
        }

        private fun send(intent: KatachiIntent) {
            sent += intent
            applyFormIntent(state, intent)?.let { state = it }
        }

        override fun value() = state.form.inputOf(feature).orEmpty()
        override fun told() = sent.size
        override fun generated() = sent.count { it == KatachiIntent.Generate }
        override fun left() = 0
        override fun inputs() = sent.filterIsInstance<KatachiIntent.Input>().filter { it.field == feature }.map { it.value }
    }

    private inner class DialogSurface : Surface {
        private val screen = ModuleTemplate(
            module(":arch-a"),
            template(
                "ui.Screen",
                parameters = listOf(stringParam("name")),
                files = listOf(file("x.kt", path = null, pattern = "feature/\${feature}/\${name}Screen.kt")),
                captures = listOf(capture("feature")),
            ),
        )
        private val host = DialogHost(listOf(screen), lister = DirectoryLister { candidates })
        override val fieldTag = KatachiTestTags.field(FieldId(screen.id, "feature"))
        override val listed = candidates.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        override fun candidateTag(value: String) = KatachiTestTags.candidate(FieldId(screen.id, "feature"), value)

        override fun ComposeUiTest.show() {
            setContent {
                IntUiTheme {
                    Box(Modifier.requiredSize(520.dp, 640.dp).background(JewelTheme.globalColors.panelBackground)) {
                        GenerateDialogContent(host.ui, host.strings, host.actions)
                    }
                }
            }
            host.settle()
            waitForIdle()
            inputOf(KatachiTestTags.field(FieldId(screen.id, "name"))).performTextInput("User")
            host.settle()
            waitForIdle()
        }

        override fun value() = host.viewModel.state.value.fields.first { it.name == "feature" }.value.orEmpty()
        override fun told() = host.calls.size
        override fun generated() = host.generated.size
        override fun left() = host.cancelled
        override fun inputs() = host.calls.filter { it.startsWith("input:feature=") }.map { it.removePrefix("input:feature=") }
    }

    private fun ComposeUiTest.isOpen(surface: Surface): Boolean =
        candidates.any { onAllNodes(hasTestTag(surface.candidateTag(it))).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.press(surface: Surface, key: Key, command: Boolean = false) {
        inputOf(surface.fieldTag).performKeyInput {
            if (command) keyDown(Key.MetaLeft)
            pressKey(key)
            if (command) keyUp(Key.MetaLeft)
        }
        waitForIdle()
    }

    /** What the popup lists for the text now typed: the candidates it is a prefix of. */
    private fun shownFor(surface: Surface, text: String) = surface.listed.filter { it.startsWith(text, ignoreCase = true) }

    /** The row an open popup starts on for [text]: the candidate that is the text, else the first; none when it lists nothing. */
    private fun startOf(text: String, surface: Surface): Int {
        val shown = shownFor(surface, text)
        return if (shown.isEmpty()) -1 else shown.indexOf(text).coerceAtLeast(0)
    }

    /** Plays [ops] on a fresh [surface]; returns what the first broken invariant says, or `null`. */
    private fun play(make: () -> Surface, ops: List<PopupOp>): String? {
        var problem: String? = null
        runComposeUiTest {
            val surface = make()
            with(surface) { show() }
            inputOf(surface.fieldTag).requestFocus()
            waitForIdle()
            // The highlighted row of the open popup, as the keys moved it (-1: none); the popup resets it when what it lists changes.
            var highlighted = -1
            for ((step, op) in ops.withIndex()) {
                val openBefore = isOpen(surface)
                val told = surface.told()
                val generated = surface.generated()
                val left = surface.left()
                val valueBefore = surface.value()
                val shown = shownFor(surface, valueBefore)
                val inputsBefore = surface.inputs()
                val label = "step ${step + 1} $op (popup ${if (openBefore) "open" else "closed"}, highlighted $highlighted, text '$valueBefore')"
                when (op) {
                    PopupOp.Down -> press(surface, Key.DirectionDown)
                    PopupOp.Chevron -> {
                        clickChevron(surface.fieldTag)
                        waitForIdle()
                    }
                    PopupOp.Up -> press(surface, Key.DirectionUp)
                    PopupOp.Enter -> press(surface, Key.Enter)
                    PopupOp.CmdEnter -> press(surface, Key.Enter, command = true)
                    PopupOp.Esc -> press(surface, Key.Escape)
                    is PopupOp.Type -> {
                        inputOf(surface.fieldTag).performTextReplacement(op.text)
                        waitForIdle()
                    }
                    is PopupOp.Click -> if (openBefore) {
                        shown.getOrNull(op.index % shown.size.coerceAtLeast(1))?.let {
                            onNodeWithTag(surface.candidateTag(it)).performClick()
                            waitForIdle()
                        }
                    }
                }
                val openAfter = isOpen(surface)
                if (openBefore) reach("${op::class.simpleName} with the popup open")
                if (openBefore && op == PopupOp.Enter && highlighted >= 0) reach("Enter took a highlighted candidate")
                if (!openBefore && openAfter) reach(if (op == PopupOp.Chevron) "popup opened by the chevron" else "popup opened")
                if (openBefore && (op == PopupOp.Esc)) {
                    if (openAfter) problem = "$label: Esc left the popup open"
                    else if (surface.told() != told || surface.left() != left) problem = "$label: Esc of an open popup told the screen something (${surface.told() - told} calls, cancelled ${surface.left() - left})"
                    else runCatching { inputOf(surface.fieldTag).assertIsFocused() }.exceptionOrNull()
                        ?.let { problem = "$label: the field lost the focus: ${it.message}" }
                }
                if (problem == null && openBefore && (op == PopupOp.Enter || op == PopupOp.CmdEnter)) {
                    if (surface.generated() != generated) problem = "$label: generated with the popup open"
                    else if (openAfter) problem = "$label: the popup is still open"
                    else if (op == PopupOp.Enter && highlighted >= 0) {
                        val taken = shown.getOrNull(highlighted)
                        val inputs = surface.inputs()
                        if (surface.value() != taken) problem = "$label: took '${surface.value()}', not the highlighted $taken"
                        else if (taken != valueBefore && inputs != null && inputs.lastOrNull() != taken) {
                            problem = "$label: told ${inputs.drop(inputsBefore?.size ?: 0)}, not the input '$taken' typing it would tell"
                        }
                    } else if (op == PopupOp.Enter) {
                        // Nothing highlighted: Enter takes nothing, tells nothing and only closes the popup.
                        if (surface.value() != valueBefore) problem = "$label: nothing was highlighted and Enter changed the text to '${surface.value()}'"
                        else if (surface.inputs() != inputsBefore) problem = "$label: nothing was highlighted and Enter told ${surface.inputs()?.drop(inputsBefore?.size ?: 0)}"
                    }
                }
                if (problem == null && op is PopupOp.Click && openBefore && shown.isNotEmpty()) {
                    val picked = shown[op.index % shown.size]
                    val inputs = surface.inputs()
                    if (surface.value() != picked) problem = "$label: clicked '$picked' and the field holds '${surface.value()}'"
                    else if (openAfter) problem = "$label: the popup is still open after a click"
                    else if (inputs != null && inputs.lastOrNull() != picked) problem = "$label: told ${inputs.drop(inputsBefore?.size ?: 0)}, not the input '$picked' typing it would tell"
                }
                if (problem != null) break
                highlighted = when {
                    !openAfter -> -1
                    // An open popup starts on the candidate the text is, else on the first; what it lists changing starts it again.
                    !openBefore -> startOf(surface.value(), surface)
                    op is PopupOp.Type -> if (surface.value() == valueBefore) highlighted else startOf(surface.value(), surface)
                    op == PopupOp.Down -> (highlighted + 1).coerceAtMost(shownFor(surface, surface.value()).lastIndex)
                    op == PopupOp.Up -> (highlighted - 1).coerceAtLeast(0)
                    else -> highlighted
                }
            }
        }
        return problem
    }

    private fun check(name: String, make: () -> Surface, sequences: Int, length: Int) {
        val random = Random(seed)
        repeat((sequences * scale).toInt().coerceAtLeast(1)) {
            val ops = opsOf(random, length)
            val first = play(make, ops) ?: return@repeat
            // Drop one step at a time while it still fails.
            var shortest = ops
            var message = first
            var shrunk = true
            while (shrunk && shortest.size > 1) {
                shrunk = false
                for (index in shortest.indices) {
                    val candidate = shortest.filterIndexed { i, _ -> i != index }
                    val failure = play(make, candidate) ?: continue
                    shortest = candidate
                    message = failure
                    shrunk = true
                    break
                }
            }
            throw AssertionError("$name (seed $seed): $message\n  ops: $shortest")
        }
        println("[pbt] candidate popup $name: reached=$reached (seed=$seed)")
        for (key in listOf("Esc with the popup open", "Enter with the popup open", "CmdEnter with the popup open", "Click with the popup open", "Enter took a highlighted candidate", "popup opened", "popup opened by the chevron")) {
            assertTrue("$name never reached '$key': $reached", (reached[key] ?: 0) > 0)
        }
    }

    @Test
    fun `ツールウィンドウでどんなキーとクリックの列でも開いているEscは閉じるだけで開いているEnterは生成せず選んだ候補が値になる`() {
        check("tool window", { ToolWindowSurface() }, sequences = 14, length = 10)
    }

    @Test
    fun `ダイアログでどんなキーとクリックの列でも開いているEscは閉じるだけで開いているEnterは生成せず選んだ候補が値になる`() {
        check("dialog", { DialogSurface() }, sequences = 14, length = 10)
    }
}
