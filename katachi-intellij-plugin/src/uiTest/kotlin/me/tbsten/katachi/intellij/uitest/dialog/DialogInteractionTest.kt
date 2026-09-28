package me.tbsten.katachi.intellij.uitest.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.testing.booleanParam
import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.enumParam
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogContent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generate dialog's parts operated through the real Composables on standalone Compose, behind
 * the real ViewModel (see [DialogHost]): the select boxes, the input fields of each type, the cursor's
 * start and Tab, and Enter. What each part does once pressed is asserted on the ViewModel's state.
 *
 * | part | test |
 * |---|---|
 * | template select box | 開いて選ぶ |
 * | definition select box | 複数の定義 |
 * | text field | 打った値 |
 * | Boolean checkbox | チェックボックス |
 * | enum select box | 列挙 |
 * | seeded capture | 初期値入り |
 * | initial cursor, Tab | 初期フォーカス, Tab |
 * | Enter | Enter |
 */
@OptIn(ExperimentalTestApi::class)
class DialogInteractionTest {
    private val archA = module(":arch-a")
    private val archB = module(":arch-b")

    private val screen = ModuleTemplate(
        archA,
        template(
            "ui.Screen",
            parameters = listOf(stringParam("name"), booleanParam("preview", "true"), enumParam("scope", listOf("Screen", "App"), default = null), stringParam("entity")),
            files = listOf(file("x.kt", path = null, pattern = "feature/\${feature}/ui/\${name}Screen.kt")),
            captures = listOf(capture("feature")),
        ),
    )
    private val repository = ModuleTemplate(
        archA,
        template("data.Repository", parameters = listOf(stringParam("entity")), files = listOf(file("x.kt", path = null, pattern = "data/\${name}Repository.kt")), captures = listOf(capture("name"))),
    )
    private val empty = ModuleTemplate(archA, template("misc.Marker", parameters = emptyList(), files = listOf(file("x.kt", path = null, pattern = "Marker.kt"))))
    private val many = ModuleTemplate(
        archA,
        template("misc.Wide", parameters = (1..8).map { stringParam("field$it") }, files = listOf(file("x.kt", path = null, pattern = "Wide.kt"))),
    )
    private val otherScreen = ModuleTemplate(archB, template("ui.Screen", parameters = listOf(stringParam("title")), files = listOf(file("x.kt", path = null, pattern = "app/Screen.kt"))))

    private fun field(model: ModuleTemplate, name: String) = KatachiTestTags.field(FieldId(model.id, name))

    private fun ComposeUiTest.show(host: DialogHost, width: Int = 520, height: Int = 640) {
        setContent {
            IntUiTheme {
                Box(Modifier.requiredSize(width.dp, height.dp).background(JewelTheme.globalColors.panelBackground)) {
                    GenerateDialogContent(host.ui, host.strings, host.actions)
                }
            }
        }
        waitForIdle()
    }

    /**
     * Opens the select box tagged [tag] and picks the item with [text] in its popup. The click that
     * follows a pick in the same box does not open the popup (the box is focused and the first press is
     * taken by Jewel), so a second click is made when no popup came up.
     */
    private fun ComposeUiTest.pick(tag: String, text: String) {
        val popup = hasTestTag("Jewel.ComboBox.Popup")
        onNodeWithTag(tag).performClick()
        waitForIdle()
        if (onAllNodes(popup, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            onNodeWithTag(tag).performClick()
            waitForIdle()
        }
        onNode(hasText(text) and hasAnyAncestor(hasTestTag("Jewel.ComboBox.Popup")), useUnmergedTree = true).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.pathText(): String =
        onNodeWithTag(KatachiTestTags.DIALOG_TARGET_PATH).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun ComposeUiTest.press(tag: String, key: Key) {
        onNodeWithTag(tag).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun DialogHost.valueOf(name: String): String? = viewModel.state.value.fields.first { it.name == name }.value

    // covers: 論点1
    @Test
    fun `テンプレートのセレクトを開いて選ぶと選んだものに切り替わり欄と生成先が変わる`() = runComposeUiTest {
        val host = DialogHost(listOf(screen, repository), initial = screen)
        show(host)
        assertEquals(listOf("ui.Screen", "data.Repository"), host.ui.templateOptions)
        pick(KatachiTestTags.DIALOG_TEMPLATE, "data.Repository")
        assertEquals(listOf("template:1"), host.calls)
        assertEquals(repository.id, host.viewModel.state.value.selectedTemplate)
        assertEquals("data/\${name}Repository.kt", pathText())
        onNodeWithTag(field(repository, "entity")).assertExists()
        onNodeWithTag(field(screen, "name")).assertDoesNotExist()

        pick(KatachiTestTags.DIALOG_TEMPLATE, "ui.Screen")
        assertEquals(screen.id, host.viewModel.state.value.selectedTemplate)
        onNodeWithTag(field(screen, "name")).assertExists()
    }

    // covers: 論点1, 論点7
    @Test
    fun `定義が1つならセレクトが無く2つなら出て選ぶと別の定義のテンプレートに切り替わる`() = runComposeUiTest {
        val single = DialogHost(listOf(screen, repository), initial = screen)
        show(single)
        onNodeWithTag(KatachiTestTags.DIALOG_DEFINITION).assertDoesNotExist()
    }

    // covers: 論点7
    @Test
    fun `定義のセレクトから別の定義を選ぶとその定義のテンプレートだけが候補になり戻せる`() = runComposeUiTest {
        val host = DialogHost(listOf(screen, repository, otherScreen), initial = screen)
        show(host)
        onNodeWithTag(KatachiTestTags.DIALOG_DEFINITION).assertExists()
        assertEquals(listOf(":arch-a", ":arch-b"), host.ui.definitionOptions)

        pick(KatachiTestTags.DIALOG_DEFINITION, ":arch-b")
        assertEquals(otherScreen.id, host.viewModel.state.value.selectedTemplate)
        assertEquals(listOf("ui.Screen"), host.ui.templateOptions)
        onNodeWithTag(field(otherScreen, "title")).assertExists()
        assertEquals("app/Screen.kt", pathText())

        pick(KatachiTestTags.DIALOG_DEFINITION, ":arch-a")
        assertEquals(listOf("ui.Screen", "data.Repository"), host.ui.templateOptions)
        onNodeWithTag(field(otherScreen, "title")).assertDoesNotExist()
    }

    // covers: 論点1, 論点2
    @Test
    fun `文字欄に打つと値が届き生成先と生成の可否に反映され消すと戻る`() = runComposeUiTest {
        val host = DialogHost(listOf(repository), initial = repository)
        show(host)
        assertFalse(host.ui.canGenerate)
        onNodeWithTag(field(repository, "name")).performTextInput("User")
        onNodeWithTag(field(repository, "entity")).performTextInput("Account")
        waitForIdle()
        assertEquals("User", host.valueOf("name"))
        assertEquals("Account", host.valueOf("entity"))
        assertEquals("data/UserRepository.kt", pathText())
        assertTrue(host.ui.canGenerate)

        onNodeWithTag(field(repository, "name")).performTextReplacement("")
        waitForIdle()
        assertEquals("data/\${name}Repository.kt", pathText())
        assertFalse(host.ui.canGenerate)
    }

    // covers: 論点1
    @Test
    fun `真偽値のチェックボックスを押すたびに値が反転して届く`() = runComposeUiTest {
        val host = DialogHost(listOf(screen), initial = screen)
        show(host)
        onNodeWithTag(field(screen, "preview")).performClick()
        waitForIdle()
        assertEquals("false", host.valueOf("preview"))
        onNodeWithTag(field(screen, "preview")).performClick()
        waitForIdle()
        assertEquals("true", host.valueOf("preview"))
        assertEquals(listOf("input:preview=false", "input:preview=true"), host.calls)
    }

    // covers: 論点1
    @Test
    fun `列挙のセレクトを開いて選ぶと選んだ値が届く`() = runComposeUiTest {
        val host = DialogHost(listOf(screen), initial = screen)
        show(host)
        assertNull(host.valueOf("scope")?.takeIf { it.isNotEmpty() })
        pick(field(screen, "scope"), "App")
        assertEquals("App", host.valueOf("scope"))
        pick(field(screen, "scope"), "Screen")
        assertEquals("Screen", host.valueOf("scope"))
    }

    // covers: 論点1
    @Test
    fun `初期値入りのcaptureは初期値が入って出て書き換えると生成先も書き換わる`() = runComposeUiTest {
        val host = DialogHost(listOf(screen), initial = screen, seeds = mapOf("feature" to "home"))
        show(host)
        onNodeWithTag(field(screen, "feature")).assertEditableTextIs("home")
        assertEquals("feature/home/ui/\${name}Screen.kt", pathText())

        onNodeWithTag(field(screen, "feature")).performTextReplacement("settings")
        waitForIdle()
        assertEquals("settings", host.valueOf("feature"))
        assertEquals("feature/settings/ui/\${name}Screen.kt", pathText())
    }

    // covers: 論点1
    @Test
    fun `初期フォーカスは最初の空の必須欄に置かれ初期値で埋まっていれば次の空欄に置かれる`() = runComposeUiTest {
        val empty = DialogHost(listOf(screen), initial = screen)
        show(empty)
        onNodeWithTag(field(screen, "feature")).assertIsFocused()
    }

    // covers: 論点1
    @Test
    fun `capture が初期値で埋まっているとき初期フォーカスは次の空欄に置かれる`() = runComposeUiTest {
        val seeded = DialogHost(listOf(screen), initial = screen, seeds = mapOf("feature" to "home"))
        show(seeded)
        onNodeWithTag(field(screen, "name")).assertIsFocused()
        onNodeWithTag(field(screen, "feature")).assertIsNotFocused()
    }

    // covers: 論点1
    @Test
    fun `テンプレートを選び直すとカーソルは新しいテンプレートの最初の空欄に移る`() = runComposeUiTest {
        val host = DialogHost(listOf(screen, repository), initial = screen)
        show(host)
        pick(KatachiTestTags.DIALOG_TEMPLATE, "data.Repository")
        onNodeWithTag(field(repository, "name")).assertIsFocused()
    }

    // covers: 論点1
    @Test
    fun `Tabで次の欄にフォーカスが移りTabの文字は入らない`() = runComposeUiTest {
        val host = DialogHost(listOf(repository), initial = repository)
        show(host)
        onNodeWithTag(field(repository, "name")).assertIsFocused()
        press(field(repository, "name"), Key.Tab)
        onNodeWithTag(field(repository, "entity")).assertIsFocused()
        onNodeWithTag(field(repository, "name")).assertIsNotFocused()
        assertEquals(emptyList<String>(), host.calls)
    }

    // covers: 論点1, 論点2
    @Test
    fun `Enterは必須が埋まるまで何もせず埋まると生成の要求を出す`() = runComposeUiTest {
        val host = DialogHost(listOf(repository), initial = repository)
        show(host)
        press(field(repository, "name"), Key.Enter)
        assertEquals(emptyList<String>(), host.calls)

        onNodeWithTag(field(repository, "name")).performTextInput("User")
        onNodeWithTag(field(repository, "entity")).performTextInput("Account")
        waitForIdle()
        press(field(repository, "entity"), Key.Enter)
        assertEquals(1, host.generated.size)
        val request = host.generated.single()
        assertEquals(repository.id, request?.template?.id)
        assertEquals(listOf("name" to "User", "entity" to "Account"), request?.args?.filter { it.first in setOf("name", "entity") })
    }

    // covers: 論点1
    @Test
    fun `パラメータが0個のテンプレートは入力欄もフォームも無く生成先が固定で生成できる`() = runComposeUiTest {
        val host = DialogHost(listOf(empty, screen), initial = empty)
        show(host)
        onNodeWithTag(KatachiTestTags.DIALOG_FORM).assertDoesNotExist()
        assertEquals("Marker.kt", pathText())
        assertTrue(host.ui.canGenerate)
    }

    // covers: 論点1, 論点2
    @Test
    fun `欄が1つも無いテンプレートでもセレクトにカーソルがあればEnterで生成の要求が出る`() = runComposeUiTest {
        val host = DialogHost(listOf(empty), initial = empty)
        show(host)
        onNodeWithTag(KatachiTestTags.DIALOG_TEMPLATE).performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
        press(KatachiTestTags.DIALOG_TEMPLATE, Key.Enter)
        assertEquals(listOf("generate"), host.calls)
        assertEquals(empty.id, host.generated.single()?.template?.id)
    }

    // covers: 論点1
    @Test
    fun `別のテンプレートに切り替えても同じ名前の欄に打った値は引き継がれる`() = runComposeUiTest {
        val host = DialogHost(listOf(screen, repository), initial = screen)
        show(host)
        onNodeWithTag(field(screen, "entity")).performTextInput("Account")
        waitForIdle()
        pick(KatachiTestTags.DIALOG_TEMPLATE, "data.Repository")
        onNodeWithTag(field(repository, "entity")).assertEditableTextIs("Account")
        onNodeWithTag(field(repository, "name")).assertEditableTextIs("")
    }

    // covers: 論点1
    @Test
    fun `ダイアログには複数行の切り替えとリンクの部品が出ない`() = runComposeUiTest {
        val host = DialogHost(listOf(screen), initial = screen)
        show(host)
        onNodeWithTag(field(screen, "name")).assertExists()
        onNodeWithTag(KatachiTestTags.multiline(FieldId(screen.id, "name"))).assertDoesNotExist()
        onNodeWithTag(KatachiTestTags.multiline(FieldId(screen.id, "feature"))).assertDoesNotExist()
    }

    // covers: 論点1
    @Test
    fun `パラメータが多数でも全部の欄が出て低い窓ではスクロールして末尾の欄に打てる`() = runComposeUiTest {
        val host = DialogHost(listOf(many), initial = many)
        show(host, height = 300)
        (1..8).forEach { onNodeWithTag(field(many, "field$it")).assertExists() }
        onNodeWithTag(field(many, "field8")).performScrollTo().performTextInput("last")
        waitForIdle()
        assertEquals("last", host.valueOf("field8"))
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertEditableTextIs(expected: String) =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))
