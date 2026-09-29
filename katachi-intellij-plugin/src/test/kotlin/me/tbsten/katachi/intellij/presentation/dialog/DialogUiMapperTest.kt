package me.tbsten.katachi.intellij.presentation.dialog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.FieldUi
import me.tbsten.katachi.intellij.presentation.LinkUi
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.testing.FakeCaptureSeedPort
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.booleanParam
import me.tbsten.katachi.intellij.testing.branch
import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.enumParam
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.intParam
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.newMenuDirectory
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import me.tbsten.katachi.intellij.ui.dialog.ListNoticeUi
import me.tbsten.katachi.intellij.ui.dialog.PropertiesGenerateDialogStrings
import me.tbsten.katachi.intellij.ui.dialog.TargetNoticeUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [dialogUiStateOf]: what the Composable is given, from the ViewModel's state, in both languages. */
class DialogUiMapperTest {
    private val archA = module(":arch-a")
    private val archB = module(":arch-b")
    private val english = PropertiesGenerateDialogStrings.english()
    private val japanese = PropertiesGenerateDialogStrings.japanese()

    private val edt = ManualDispatcher()
    private val background = ManualDispatcher()
    private var targetState: TargetState = TargetState.Absent

    private val component = ModuleTemplate(
        archA,
        template(
            "ui.Component",
            title = "Component",
            summary = "A screen part",
            parameters = listOf(
                stringParam("name"),
                stringParam("label", default = "\${name}"),
                intParam("columns", default = "2"),
                enumParam("kind", listOf("Compose", "View"), default = null),
                booleanParam("withImpl"),
                stringParam("implSuffix", default = "Impl"),
            ),
            files = listOf(file("x.kt", path = null, pattern = "feature/\${feature}/\${name}Component.kt")),
            branches = listOf(branch("withImpl", "false", removedParameters = listOf("implSuffix"))),
            captures = listOf(capture("feature")),
        ),
    )
    private val other = ModuleTemplate(archB, template("ui.Screen", files = listOf(file("x.kt", path = null, pattern = "app/\${name}Screen.kt"))))

    private fun dialog(templates: List<ModuleTemplate> = listOf(component)): GenerateDialogViewModel = GenerateDialogViewModel(
        scope = CoroutineScope(edt + Job()),
        request = GenerateDialogRequest(newMenuDirectory("feature/home"), component.id, mapOf("feature" to "home")),
        candidates = templates,
        seeds = FakeCaptureSeedPort(),
        checkTarget = { targetState },
        rootOf = { module: KatachiModule -> module.linkedRootPath },
        checkContext = background,
        settle = {},
    )

    private fun GenerateDialogViewModel.settled(): GenerateDialogState {
        while (edt.pending + background.pending > 0) {
            edt.runAll()
            background.runAll()
        }
        return state.value
    }

    private fun GenerateDialogViewModel.type(name: String, value: String) = apply { dispatch(GenerateDialogIntent.Input(name, value)) }

    private fun fieldsOf(vm: GenerateDialogViewModel, strings: PropertiesGenerateDialogStrings = english) = dialogUiStateOf(vm.state.value, strings).fields.associateBy { it.id.parameterName }

    // covers: 論点8
    @Test
    fun `パラメータの型ごとに、ツールウィンドウと同じ入力部品の種類になる`() {
        val fields = fieldsOf(dialog())

        assertTrue(fields.getValue("feature") is FieldUi.Text)
        assertTrue(fields.getValue("columns").let { it is FieldUi.Text && it.isNumber })
        assertTrue(fields.getValue("kind") is FieldUi.Choice)
        assertTrue(fields.getValue("withImpl") is FieldUi.Bool)
        assertEquals(listOf("feature", "name", "label", "columns", "kind", "withImpl", "implSuffix"), fields.keys.toList())
    }

    // covers: 論点1
    @Test
    fun `起点から決まった capture は値が入り、決まらない必須項目は空でエラーもまだ出ない`() {
        val fields = fieldsOf(dialog())

        assertEquals("home", (fields.getValue("feature") as FieldUi.Text).value)
        val name = fields.getValue("name") as FieldUi.Text
        assertEquals("", name.value)
        assertNull(name.error)
        assertTrue(name.isRequired)
    }

    // covers: 論点8
    @Test
    fun `capture の説明とエラーと選択の案内は選んだ言語の文言になる`() {
        val vm = dialog().type("feature", "a/b").type("name", "")

        val en = fieldsOf(vm, english)
        val ja = fieldsOf(vm, japanese)

        assertEquals(english.captureSeparatorError, (en.getValue("feature") as FieldUi.Text).error)
        assertEquals(japanese.captureSeparatorError, (ja.getValue("feature") as FieldUi.Text).error)
        assertTrue((en.getValue("feature") as FieldUi.Text).hint!!.contains("<feature>"))
        assertEquals(english.requiredError, (en.getValue("name") as FieldUi.Text).error)
        assertEquals(english.chooseOne, (en.getValue("kind") as FieldUi.Choice).placeholder)
        assertEquals(japanese.chooseOne, (ja.getValue("kind") as FieldUi.Choice).placeholder)
    }

    // covers: 論点1
    @Test
    fun `選ばれていない分岐のパラメータは畳まれた行になり、リンクは付かない`() {
        val vm = dialog().type("withImpl", "false")

        val fields = fieldsOf(vm)

        assertTrue(fields.getValue("implSuffix") is FieldUi.Collapsed)
        assertEquals(english.collapsedField("implSuffix", "withImpl", "true"), fields.getValue("implSuffix").label)
        fields.values.filterIsInstance<FieldUi.Text>().forEach { assertEquals(LinkUi.None, it.link) }
    }

    // covers: 論点1
    @Test
    fun `最初の空の必須項目にカーソルを置く候補になり、埋まると次の空の項目へ移る`() {
        val vm = dialog()
        assertEquals("name", dialogUiStateOf(vm.state.value, english).firstEmptyRequired?.parameterName)

        vm.type("name", "Profile")
        assertEquals("kind", dialogUiStateOf(vm.state.value, english).firstEmptyRequired?.parameterName)

        vm.type("kind", "Compose")
        assertNull(dialogUiStateOf(vm.state.value, english).firstEmptyRequired)
    }

    // covers: 論点6
    @Test
    fun `既にあるファイルの予告は3種に対応し、katachi が決める場合は何も出さない`() {
        val vm = dialog().type("name", "Profile").type("kind", "Compose")
        val cases = listOf(
            TargetState.Absent to TargetNoticeUi.WillCreate,
            TargetState.Empty to TargetNoticeUi.WillOverwriteEmpty,
            TargetState.OwnProvisional to TargetNoticeUi.WillOverwriteEmpty,
            TargetState.HasContent to TargetNoticeUi.CannotOverwrite,
        )
        for ((state, expected) in cases) {
            targetState = state
            vm.dispatch(GenerateDialogIntent.FilesChangedOutside)
            assertEquals("$state", expected, dialogUiStateOf(vm.settled(), english).targetNotice)
        }
        assertEquals(false, dialogUiStateOf(vm.state.value, english).canGenerate)
    }

    // covers: 論点7
    @Test
    fun `定義のセレクトは2つ以上のときだけ出て、テンプレートのセレクトは1つでも出る`() {
        val single = dialogUiStateOf(dialog().state.value, english)
        assertEquals(listOf("ui.Component › ${component.template.title}"), single.templateOptions)
        assertTrue(single.definitionOptions.isEmpty())

        val two = dialogUiStateOf(dialog(listOf(component, other)).state.value, english)
        assertEquals(listOf(":arch-a", ":arch-b").size, two.definitionOptions.size)
        assertEquals(0, two.selectedDefinition)
    }

    // covers: 論点0
    @Test
    fun `一覧が変わったときの案内は、切り替え先の題名と候補なしの2種になる`() {
        val vm = dialog(listOf(component, ModuleTemplate(archA, template("ui.Other", title = "Other"))))
        vm.dispatch(GenerateDialogIntent.ListChanged(listOf(ModuleTemplate(archA, template("ui.Other", title = "Other")))))
        assertEquals(ListNoticeUi.TemplateReplaced("Other"), dialogUiStateOf(vm.settled(), english).listNotice)

        vm.dispatch(GenerateDialogIntent.ListChanged(emptyList()))
        assertEquals(ListNoticeUi.NoCandidates, dialogUiStateOf(vm.settled(), english).listNotice)
    }
}
