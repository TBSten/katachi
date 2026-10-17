package me.tbsten.katachi.intellij.uitest.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.data.placement.DirectoryLister
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogContent
import me.tbsten.katachi.intellij.uitest.ui.inputOf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keys of the generate dialog around a capture field's candidate popup (design ide-review-screens 3.4), through the real
 * Composables: Esc closes only the popup while it is open and cancels the dialog otherwise, and Enter takes the highlighted
 * candidate while the popup is open (generating nothing), and generates when it is closed.
 */
@OptIn(ExperimentalTestApi::class)
class DialogCandidateKeysTest {
    private val screen = ModuleTemplate(
        module(":arch-a"),
        template(
            "ui.Screen",
            parameters = listOf(stringParam("name")),
            files = listOf(file("x.kt", path = null, pattern = "feature/\${feature}/\${name}Screen.kt")),
            captures = listOf(capture("feature")),
        ),
    )
    private val featureTag = KatachiTestTags.field(FieldId(screen.id, "feature"))
    private val nameTag = KatachiTestTags.field(FieldId(screen.id, "name"))

    private fun ComposeUiTest.show(host: DialogHost) {
        setContent {
            IntUiTheme {
                Box(Modifier.requiredSize(520.dp, 640.dp).background(JewelTheme.globalColors.panelBackground)) {
                    GenerateDialogContent(host.ui, host.strings, host.actions)
                }
            }
        }
        host.settle()
        waitForIdle()
    }

    /** The dialog with `name` filled and the candidate field focused (its candidates already read). */
    private fun ComposeUiTest.opened(): DialogHost {
        val host = DialogHost(listOf(screen), lister = DirectoryLister { listOf("home", "settings") })
        show(host)
        inputOf(nameTag).performTextInput("User")
        host.settle()
        waitForIdle()
        inputOf(featureTag).requestFocus()
        return host
    }

    private fun ComposeUiTest.press(tag: String, key: Key) {
        inputOf(tag).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.candidateShown(value: String): Boolean =
        onAllNodes(hasTestTag(KatachiTestTags.candidate(FieldId(screen.id, "feature"), value))).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `開いている間のEscはポップアップだけ閉じダイアログは閉じず閉じた後のEscでダイアログを閉じる`() = runComposeUiTest {
        val host = opened()
        press(featureTag, Key.DirectionDown)
        assertTrue(candidateShown("home"))

        press(featureTag, Key.Escape)
        assertFalse(candidateShown("home"))
        assertEquals("ポップアップが開いている間のEscはダイアログを閉じない", 0, host.cancelled)

        press(featureTag, Key.Escape)
        assertEquals(1, host.cancelled)
    }

    @Test
    fun `開いている間のEnterは強調中の候補を入れて閉じ生成は呼ばない`() = runComposeUiTest {
        val host = opened()
        // The first Down opens the popup on the first candidate.
        press(featureTag, Key.DirectionDown)
        press(featureTag, Key.Enter)

        assertEquals("home", host.valueOf("feature"))
        assertFalse(candidateShown("home"))
        assertTrue("開いている間のEnterは生成しない: ${host.calls}", "generate" !in host.calls)
        assertTrue(host.generated.isEmpty())
    }

    @Test
    fun `開いている間のコマンドEnterは強調中の候補を入れて閉じ生成は呼ばない`() = runComposeUiTest {
        val host = opened()
        // The first Down opens the popup on the first candidate.
        press(featureTag, Key.DirectionDown)
        inputOf(featureTag).performKeyInput {
            keyDown(Key.MetaLeft)
            pressKey(Key.Enter)
            keyUp(Key.MetaLeft)
        }
        waitForIdle()

        assertEquals("home", host.valueOf("feature"))
        assertFalse(candidateShown("home"))
        assertTrue("開いている間の生成のショートカットは生成しない: ${host.calls}", "generate" !in host.calls)
        assertTrue(host.generated.isEmpty())
    }

    @Test
    fun `閉じているEnterは必須が埋まっていれば生成の要求を出す`() = runComposeUiTest {
        val host = opened()
        inputOf(featureTag).performTextInput("home")
        host.settle()
        waitForIdle()

        press(featureTag, Key.Enter)

        assertEquals(1, host.generated.size)
        assertEquals(screen.id, host.generated.single()?.template?.id)
    }

    private fun DialogHost.valueOf(name: String): String? = viewModel.state.value.fields.first { it.name == name }.value
}
