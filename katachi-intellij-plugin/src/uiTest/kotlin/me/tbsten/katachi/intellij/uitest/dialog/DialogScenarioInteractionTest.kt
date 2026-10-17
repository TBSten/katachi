package me.tbsten.katachi.intellij.uitest.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.data.placement.DirectoryLister
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogIntent
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
 * The generate dialog operated the way a user does, one column of operations per test (plan ide-review-screens 4.3, the dialog
 * side): a candidate picked with the keys and what "the file to create" shows after it, Esc in its order, the disk changing under
 * an open dialog, a multi-line field, and the `<name>` placeholder. Every test goes through the real Composables.
 */
@OptIn(ExperimentalTestApi::class)
class DialogScenarioInteractionTest {
    private val screen = ModuleTemplate(
        module(":arch-a"),
        template(
            "ui.Screen",
            parameters = listOf(stringParam("name"), stringParam("entity")),
            files = listOf(file("x.kt", path = null, pattern = "feature/\${feature}/\${name}.kt")),
            captures = listOf(capture("feature")),
        ),
    )
    private val feature = FieldId(screen.id, "feature")
    private val featureTag = KatachiTestTags.field(feature)
    private val nameTag = KatachiTestTags.field(FieldId(screen.id, "name"))
    private val entityTag = KatachiTestTags.field(FieldId(screen.id, "entity"))

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

    private fun hostOf(vararg directories: String): Pair<DialogHost, MutableList<String>> {
        val disk = directories.toMutableList()
        return DialogHost(listOf(screen), lister = DirectoryLister { disk.toList() }) to disk
    }

    private fun ComposeUiTest.type(host: DialogHost, tag: String, text: String) {
        inputOf(tag).performTextInput(text)
        host.settle()
        waitForIdle()
    }

    private fun ComposeUiTest.press(tag: String, key: Key) {
        inputOf(tag).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.pressWithCommand(tag: String, key: Key) {
        inputOf(tag).performKeyInput {
            keyDown(Key.MetaLeft)
            pressKey(key)
            keyUp(Key.MetaLeft)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.candidateShown(value: String): Boolean =
        onAllNodes(hasTestTag(KatachiTestTags.candidate(feature, value))).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.pathText(): String =
        onNodeWithTag(KatachiTestTags.DIALOG_TARGET_PATH).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun DialogHost.valueOf(name: String): String? = viewModel.state.value.fields.first { it.name == name }.value

    @Test
    fun `欄にフォーカスして下を二回押しEnterを押すと二番目の候補が入り作るファイルがその候補の下になる`() = runComposeUiTest {
        val (host, _) = hostOf("home", "settings", "profile")
        show(host)
        type(host, nameTag, "User")
        inputOf(featureTag).requestFocus()

        repeat(2) { press(featureTag, Key.DirectionDown) }
        press(featureTag, Key.Enter)

        // The popup lists the candidates in name order (home, profile, settings): the second one is profile.
        assertEquals("profile", host.valueOf("feature"))
        assertFalse(candidateShown("profile"))
        assertEquals("feature/profile/User.kt", pathText())
        assertTrue(host.generated.isEmpty())
    }

    @Test
    fun `ポップアップを開いてEscで閉じ閉じた後のEscでダイアログが閉じて結果は無いまま`() = runComposeUiTest {
        val (host, _) = hostOf("home", "settings")
        show(host)
        type(host, nameTag, "User")
        inputOf(featureTag).requestFocus()
        press(featureTag, Key.DirectionDown)
        assertTrue(candidateShown("home"))

        press(featureTag, Key.Escape)
        assertFalse(candidateShown("home"))
        assertEquals("1 回目の Esc はポップアップだけを閉じる", 0, host.cancelled)

        press(featureTag, Key.Escape)
        assertEquals("2 回目の Esc でダイアログが閉じる", 1, host.cancelled)
        assertTrue("閉じたダイアログは何も生成していない", host.generated.isEmpty())
    }

    @Test
    fun `ダイアログを開いたまま偽のディスクに profile を足して読み直されポップアップを開き直すと profile がある`() = runComposeUiTest {
        val (host, disk) = hostOf("home", "settings")
        show(host)
        type(host, nameTag, "User")
        inputOf(featureTag).requestFocus()
        press(featureTag, Key.DirectionDown)
        assertTrue(candidateShown("home"))
        assertFalse(candidateShown("profile"))
        press(featureTag, Key.Escape)

        disk += "profile"
        host.viewModel.dispatch(GenerateDialogIntent.FilesChangedOutside)
        host.settle()
        waitForIdle()
        press(featureTag, Key.DirectionDown)

        assertTrue(listOf("home", "settings", "profile").all { candidateShown(it) })
    }

    @Test
    fun `複数行の三角を押して改行を含めて打つとEnterは改行で生成せずコマンドEnterで生成する`() = runComposeUiTest {
        val (host, _) = hostOf("home")
        show(host)
        type(host, nameTag, "User")
        type(host, featureTag, "home")
        onNodeWithTag(KatachiTestTags.multiline(FieldId(screen.id, "entity"))).performClick()
        waitForIdle()

        type(host, entityTag, "line1")
        press(entityTag, Key.Enter)
        assertTrue("Enter は改行で、生成しない", host.generated.isEmpty())
        type(host, entityTag, "line2")
        assertEquals("line1\nline2", host.valueOf("entity"))

        pressWithCommand(entityTag, Key.Enter)
        assertEquals(1, host.generated.size)
        assertEquals("line1\nline2", host.generated.single()?.args?.firstOrNull { it.first == "entity" }?.second)
    }

    @Test
    fun `名前が空のとき作るファイルは山かっこの name を見せ打つと置き換わる`() = runComposeUiTest {
        val (host, _) = hostOf("home")
        show(host)
        type(host, featureTag, "home")
        assertEquals("feature/home/<name>.kt", pathText())

        type(host, nameTag, "Profile")
        assertEquals("feature/home/Profile.kt", pathText())
    }
}
