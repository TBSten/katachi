package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.ModuleChoiceModel
import me.tbsten.katachi.intellij.model.ModulePlacementModel
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.setMember
import me.tbsten.katachi.intellij.testing.setTemplate
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A template set's row (template-set design 8.2): the badge, the member line, the search and one generation item per set. */
class TemplateSetRowsTest {
    private val arch = module(":arch-a")
    private val other = module(":arch-b")
    private val newFeature = setTemplate(
        "feature.NewFeature",
        listOf(
            setMember("feature.Screen", "Screen", "feature/\${feature}/ui/\${feature}Screen.kt"),
            setMember("feature.ViewModel", "ViewModel", "feature/\${feature}/ui/\${feature}ViewModel.kt"),
        ),
        title = "新しい機能",
    )
    private val plainA = template("data.Repository")
    private val plainB = template("domain.UseCase")
    private val setRow = ModuleTemplate(arch, newFeature)
    private val repoRow = ModuleTemplate(arch, plainA)
    private val useCaseRow = ModuleTemplate(arch, plainB)

    private fun stateOf(vararg rows: ModuleTemplate, form: FormState = FormState()) = KatachiScreenState(
        phase = ScreenPhase.Ready,
        modules = listOf(arch),
        snapshots = listOf(DescriptionSnapshot(arch, "0.3.0", rows.map { it.template }, DEFINITION_INSTANT)),
        form = form,
    )

    private fun uiRowOf(state: KatachiScreenState, row: ModuleTemplate): TemplateRowUi =
        listItemsOf(state, JapaneseKatachiStrings).filterIsInstance<ListItemUi.Row>().first { it.row.id == row.id }.row

    @Test
    fun `組の行には組のバッジとメンバの題を中黒でつないだ行が付く`() {
        val ui = uiRowOf(stateOf(setRow, repoRow), setRow)

        val set = ui.set ?: throw AssertionError("set row")
        assertEquals("組", set.badge)
        assertEquals("Screen・ViewModel", set.memberLine)
        assertEquals("メンバ 2 件: Screen・ViewModel", set.memberTooltip)
    }

    @Test
    fun `テンプレートの行には組の印が付かない`() {
        assertNull(uiRowOf(stateOf(setRow, repoRow), repoRow).set)
    }

    @Test
    fun `検索は組のメンバの題と指定にも当たる`() {
        val rows = listOf(setRow, repoRow)

        assertEquals(listOf(setRow.id), searchTemplates(rows, "viewmodel", FormState()).map { it.row.id })
        assertEquals(listOf(setRow.id), searchTemplates(rows, "feature.Screen", FormState()).map { it.row.id })
        assertTrue(searchTemplates(rows, "zzz", FormState()).isEmpty())
    }

    @Test
    fun `チェックした組は1つずつ別の生成の単位になり同じモジュールの他の行は1つにまとまる`() {
        val form = FormState(
            selected = listOf(repoRow.id, setRow.id, useCaseRow.id),
            inputs = mapOf(setRow.id to mapOf("feature" to "profile"), repoRow.id to mapOf("name" to "User"), useCaseRow.id to mapOf("name" to "User")),
        )

        val items = generationItemsOf(listOf(repoRow, setRow, useCaseRow), form)

        // The list order: the module's plain templates stand where the first of them does, the set after them.
        assertEquals(listOf(listOf(repoRow.id, useCaseRow.id), listOf(setRow.id)), items.map { it.templateIds })
        assertEquals(listOf("template" to "feature.NewFeature", "onExisting" to "fail", "feature" to "profile"), items[1].args)
        assertEquals(listOf("template" to "data.Repository,domain.UseCase", "onExisting" to "fail", "name" to "User"), items[0].args)
    }

    @Test
    fun `組が先にあれば組が先の単位になる`() {
        val form = FormState(selected = listOf(setRow.id, repoRow.id))

        val items = generationItemsOf(listOf(setRow, repoRow), form)

        assertEquals(listOf(listOf(setRow.id), listOf(repoRow.id)), items.map { it.templateIds })
    }

    @Test
    fun `別のモジュールの組と行はそれぞれ別の単位で一覧の順に並ぶ`() {
        val otherSet = ModuleTemplate(other, newFeature)
        val form = FormState(selected = listOf(setRow.id, otherSet.id))

        val items = generationItemsOf(listOf(setRow, otherSet), form)

        assertEquals(listOf(arch, other), items.map { it.module })
    }

    @Test
    fun `プレビューに失敗した組は生成の単位に入らない`() {
        val broken = ModuleTemplate(arch, setTemplate("feature.Broken", listOf(setMember("feature.Screen", "Screen")), unavailable = true))

        assertTrue(generationItemsOf(listOf(broken), FormState(selected = listOf(broken.id))).isEmpty())
    }

    @Test
    fun `組のパラメータが階層まるごとに出るなら候補を求め、ファイル名の中や混ざった階層なら素の欄のまま`() {
        val params = listOf(stringParam("feature"), stringParam("name"), stringParam("mixed"))
        val set = ModuleTemplate(
            arch,
            setTemplate(
                "feature.Mixed",
                listOf(setMember("feature.Screen", "Screen", "feature/\${feature}/ui/\${name}Screen.kt", "pre-\${mixed}/x/Y.kt")),
                parameters = params,
            ),
        )
        val form = FormState(selected = listOf(set.id), inputs = mapOf(set.id to mapOf("feature" to "a")))

        val requests = candidateRequestsOf(set, form.inputsOf(set.id), ROOT)

        assertEquals(listOf("feature"), requests.map { it.field.parameterName })
    }

    @Test
    fun `組のパラメータの欄は読んだ候補を出し、組のパラメータでない欄と素の欄には出さない`() {
        val form = FormState(selected = listOf(setRow.id), expanded = setOf(setRow.id))
        val fieldId = FieldId(setRow.id, "feature")
        val base = stateOf(setRow, form = form)

        fun fieldOf(state: KatachiScreenState): FieldUi.Text {
            val body = uiRowOf(state, setRow).body as RowBodyUi.Form
            return body.form.fields.single() as FieldUi.Text
        }

        assertNull(fieldOf(base).suggestions)
        val withCandidates = base.copy(captureCandidates = mapOf(fieldId to CandidateList(listOf("home", "settings"), directoryPattern = "feature/{value}")))
        assertEquals(listOf("home", "settings"), fieldOf(withCandidates).suggestions?.values)
    }

    @Test
    fun `組の欄は候補に無い新しい名前に新しいディレクトリの注意を出すが階層を含む値と点には出さない`() {
        val candidates = CandidateList(listOf("home", "settings"), directoryPattern = "feature/{value}")

        fun noteOf(value: String) = suggestionsOf(candidates, value, error = null, JapaneseKatachiStrings)?.newDirectoryNote

        assertEquals("feature/profile は新しいディレクトリになります", noteOf("profile"))
        assertNull(noteOf("home"))
        assertNull(noteOf("home/list"))
        assertNull(noteOf(".."))
        assertNull(noteOf(""))
    }

    @Test
    fun `モジュール capture の下のメンバのファイルにしか出ない組のパラメータには候補を求めない`() {
        val placement = ModulePlacementModel(":feature:*", listOf("feature"), listOf(ModuleChoiceModel(listOf("home"), ":feature:home", "feature/home", "feature/home/src/Page.kt")))
        val underModule = setMember("ui.Screen", "Screen").let {
            it.copy(files = listOf(file("Page.kt", path = null, pattern = "feature/\${feature}/src/Page.kt").copy(modulePlacement = placement)))
        }
        val set = ModuleTemplate(arch, setTemplate("feature.NewFeature", listOf(underModule)))
        val form = FormState(selected = listOf(set.id), inputs = mapOf(set.id to mapOf("feature" to "home")))

        assertTrue(candidateRequestsOf(set, form.inputsOf(set.id), ROOT).isEmpty())
    }

    @Test
    fun `階層まるごとの組のパラメータに階層を含む値や点を入れると生成を止める`() {
        fun blockerOf(value: String): GenerateBlocker? = generateBlockerOf(
            listOf(setRow),
            FormState(selected = listOf(setRow.id), inputs = mapOf(setRow.id to mapOf("feature" to value))),
            BusyState.Idle,
        )

        assertEquals(GenerateBlocker.InvalidField(setRow.id, "feature", FieldError.InvalidCapture(CaptureProblem.Separator)), blockerOf("home/list"))
        assertEquals(GenerateBlocker.InvalidField(setRow.id, "feature", FieldError.InvalidCapture(CaptureProblem.Dot)), blockerOf(".."))
        assertNull(blockerOf("home"))
    }

    @Test
    fun `詳細の無い組の行はバッジだけでメンバの行を出さない`() {
        val broken = ModuleTemplate(arch, setTemplate("feature.Broken", emptyList(), unavailable = true))

        val set = uiRowOf(stateOf(broken), broken).set ?: throw AssertionError("set row")

        assertEquals("組", set.badge)
        assertNull(set.memberLine)
        assertNull(set.memberTooltip)
    }

    @Test
    fun `候補の付いた組の欄は複数行の切り替えを持たず、候補の無い欄は持つ`() {
        val form = FormState(selected = listOf(setRow.id), expanded = setOf(setRow.id))
        val base = stateOf(setRow, form = form)
        val withCandidates = base.copy(
            captureCandidates = mapOf(FieldId(setRow.id, "feature") to CandidateList(listOf("home"), directoryPattern = "feature/{value}")),
        )

        fun fieldOf(state: KatachiScreenState) = (uiRowOf(state, setRow).body as RowBodyUi.Form).form.fields.single() as FieldUi.Text

        assertNull(fieldOf(withCandidates).isMultiline)
        assertNull(fieldOf(withCandidates).multilineTooltip)
        assertEquals(false, fieldOf(base).isMultiline)
    }
}
