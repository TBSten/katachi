package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.booleanParam
import me.tbsten.katachi.intellij.testing.branch
import me.tbsten.katachi.intellij.testing.modulePlacedTemplate
import me.tbsten.katachi.intellij.testing.row
import me.tbsten.katachi.intellij.testing.rows
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerateEnablementTest {
    private val rows = rowsOf("arch-a")
    private val repository = rows.row("data.Repository").id
    private val useCase = rows.row("domain.UseCase").id
    private val screen = rows.row("ui.Screen").id

    private fun form(vararg checked: Pair<TemplateId, Map<String, String>>) = FormState(
        selected = checked.map { it.first },
        inputs = checked.associate { it },
    )

    @Test
    fun `何もチェックしていなければ押せない`() {
        assertEquals(GenerateBlocker.NothingSelected, generateBlockerOf(rows, FormState(), BusyState.Idle))
    }

    @Test
    fun `必須が埋まり検証エラーが無ければ押せる`() {
        assertNull(generateBlockerOf(rows, form(repository to mapOf("name" to "User")), BusyState.Idle))
    }

    @Test
    fun `必須の空は一覧の順で最初の行の最初の欄を理由にする`() {
        val blocker = generateBlockerOf(rows, form(useCase to emptyMap(), repository to emptyMap()), BusyState.Idle)
        assertEquals(GenerateBlocker.InvalidField(repository, "name", FieldError.Required), blocker)
    }

    @Test
    fun `Intの不正入力で押せず理由に欄とエラーを出す`() {
        val blocker = generateBlockerOf(rows, form(useCase to mapOf("name" to "Login", "retries" to "１２")), BusyState.Idle)
        assertEquals(GenerateBlocker.InvalidField(useCase, "retries", FieldError.NotAnInt()), blocker)
    }

    @Test
    fun `畳んだ条件付きの欄の必須は数えない`() {
        val list = rows(
            template(
                "a/X",
                parameters = listOf(booleanParam("withImpl"), stringParam("implName")),
                branches = listOf(branch("withImpl", "false", removedParameters = listOf("implName"))),
            ),
        )
        val id = list.single().id
        val folded = FormState(selected = listOf(id), inputs = mapOf(id to mapOf("withImpl" to "false")))
        assertNull(generateBlockerOf(list, folded, BusyState.Idle))
        val shown = folded.copy(inputs = mapOf(id to mapOf("withImpl" to "true")))
        assertEquals(GenerateBlocker.InvalidField(id, "implName", FieldError.Required), generateBlockerOf(list, shown, BusyState.Idle))
    }

    @Test
    fun `フォームを畳んだ行や検索外の行もチェック中なら数える`() {
        val collapsed = form(repository to emptyMap()).copy(expanded = emptySet())
        assertEquals(GenerateBlocker.InvalidField(repository, "name", FieldError.Required), generateBlockerOf(rows, collapsed, BusyState.Idle))
    }

    @Test
    fun `生成先の決まらないファイルを含む行がチェック中なら押せない`() {
        val blocker = generateBlockerOf(rows, form(screen to mapOf("name" to "Home")), BusyState.Idle)
        assertEquals(GenerateBlocker.UnresolvedPath(screen, "HomeScreen.kt"), blocker)
    }

    @Test
    fun `生成中とキャッシュ無しの初回読み込み中は押せずキャッシュありの更新中は押せる`() {
        val ok = form(repository to mapOf("name" to "User"))
        assertEquals(GenerateBlocker.Generating, generateBlockerOf(rows, ok, BusyState.Generating))
        assertEquals(GenerateBlocker.InitialLoading, generateBlockerOf(rows, ok, BusyState.InitialLoading))
        assertNull(KatachiScreenState(form = ok, loading = LoadingState(isInitial = false)).let { generateBlockerOf(rows, it.form, it.busy) })
    }

    @Test
    fun `使えなくなった行がチェックに残っていれば押せない`() {
        val broken = rows.row("misc.Broken").id
        assertEquals(GenerateBlocker.Unavailable(broken), generateBlockerOf(rows, form(broken to emptyMap()), BusyState.Idle))
    }

    // ---- below a module capture (katachi's modulePlacements) ----

    private val componentRows = rows(
        modulePlacedTemplate(modules = mapOf("home" to ("feature/home" to "feature/home/src/Home\${name}.kt"))),
    )
    private val component = componentRows.single().id

    @Test
    fun `モジュールのcaptureに今あるモジュールを入れると押せる`() {
        assertNull(generateBlockerOf(componentRows, form(component to mapOf("feature" to "home", "name" to "Card")), BusyState.Idle))
    }

    @Test
    fun `モジュールのcaptureが未入力の間は生成先ではなくその欄の入力を理由にする`() {
        assertEquals(
            GenerateBlocker.InvalidField(component, "feature", FieldError.Required),
            generateBlockerOf(componentRows, form(component to mapOf("name" to "Card")), BusyState.Idle),
        )
    }

    @Test
    fun `今あるモジュールに無い値では押せず今ある値を理由に添える`() {
        assertEquals(
            GenerateBlocker.InvalidField(component, "feature", FieldError.NotAnExistingModule(listOf("home"))),
            generateBlockerOf(componentRows, form(component to mapOf("feature" to "hoem", "name" to "Card")), BusyState.Idle),
        )
    }

    @Test
    fun `modulePlacementsの無いkatachiではモジュールのcaptureを入れても生成先が決まらず押せない`() {
        val old = componentRows.map { row ->
            val detail = row.template.detail ?: throw AssertionError()
            row.copy(
                template = row.template.copy(
                    detail = detail.copy(
                        files = detail.files.map { it.copy(modulePlacement = null) },
                        captures = detail.captures.map { it.copy(existingModules = null) },
                    ),
                ),
            )
        }
        assertEquals(
            GenerateBlocker.UnresolvedPath(component, "<feature>Card.kt"),
            generateBlockerOf(old, form(component to mapOf("feature" to "home", "name" to "Card")), BusyState.Idle),
        )
    }
}
