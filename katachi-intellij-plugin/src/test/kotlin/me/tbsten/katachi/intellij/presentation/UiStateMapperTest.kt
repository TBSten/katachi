package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.testing.booleanParam
import me.tbsten.katachi.intellij.testing.cast
import me.tbsten.katachi.intellij.testing.branch
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.previewFailed
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UiStateMapperTest {
    private val now = Instant.parse("2026-09-27T10:00:00Z")
    private val arch = module(":arch")
    private val repository = template(
        "data/Repository",
        parameters = listOf(stringParam("name"), booleanParam("withImpl"), stringParam("implSuffix", default = "Impl")),
        files = listOf(file("\${name}Repository.kt"), file("\${name}Repository\${implSuffix}.kt")),
        branches = listOf(branch("withImpl", "false", removedFiles = listOf("\${name}Repository\${implSuffix}.kt"), removedParameters = listOf("implSuffix"))),
        title = "リポジトリ",
    )
    private val service = template("domain/Service", files = listOf(file("\${name}Service.kt")))
    private val broken = previewFailed("data/Cache")

    private fun ready(vararg modules: Pair<KatachiModule, List<TemplateModel>>) = KatachiScreenState(
        phase = ScreenPhase.Ready,
        modules = modules.map { it.first },
        snapshots = modules.map { (module, templates) -> DescriptionSnapshot(module, "0.3.0", templates, now.minusSeconds(600)) },
    )

    private fun KatachiScreenState.then(vararg intents: KatachiIntent) = intents.fold(this) { s, i -> applyFormIntent(s, i)!! }

    private fun id(template: TemplateModel, module: KatachiModule = arch) = TemplateId(module.id, template.roleName)

    private fun check(template: TemplateModel) = KatachiIntent.ToggleCheck(id(template))

    private fun input(template: TemplateModel, name: String, value: String) = KatachiIntent.Input(FieldId(id(template), name), value)

    private fun ui(state: KatachiScreenState) = uiStateOf(state, JapaneseKatachiStrings, now)

    private fun list(state: KatachiScreenState): ListUi = (ui(state).body as? BodyUi.Listing)?.list ?: error("not a list: ${ui(state).body}")

    private fun ListUi.row(template: TemplateModel): TemplateRowUi =
        items.firstNotNullOf { (it as? ListItemUi.Row)?.row?.takeIf { row -> row.id.roleName == template.roleName } }

    private fun TemplateRowUi.form(): FormUi = (body as? RowBodyUi.Form)?.form ?: error("no form: $body")

    private fun FormUi.field(name: String): FieldUi = fields.first { it.id.parameterName == name }

    @Test
    fun `初回の読み込み中は最新のタスク名を出し、タイトルの操作は停止になる`() {
        val state = ui(KatachiScreenState(loading = LoadingState(isInitial = true, currentTask = ":arch:compileTestKotlin")))
        val body = state.body as? BodyUi.InitialLoading ?: error("${state.body}")
        assertEquals("> :arch:compileTestKotlin", body.currentTask)
        assertEquals(TitleAction.Stop, state.titleAction)
    }

    @Test
    fun `キャッシュを出したままの更新は一覧の上に前回からの経過を出す`() {
        val refresh = list(ready(arch to listOf(service)).copy(loading = LoadingState(isInitial = false))).refresh
        assertEquals("更新中…（前回: 10分前）", refresh?.label)
    }

    @Test
    fun `モジュールが1つなら見出し帯を出さず、グループが変わるたびに小見出しを出す`() {
        val items = list(ready(arch to listOf(repository, service))).items
        assertTrue(items.none { it is ListItemUi.ModuleHeader })
        assertEquals(listOf("data", "domain"), items.filterIsInstance<ListItemUi.GroupHeader>().map { it.title })
    }

    @Test
    fun `モジュールが2つ以上なら見出し帯に選択数と件数を出し、畳んだモジュールの行は出さない`() {
        val other = module(":other")
        val state = ready(arch to listOf(repository, service), other to listOf(service))
            .then(check(service), KatachiIntent.ToggleModule(other.id))
        val headers = list(state).items.filterIsInstance<ListItemUi.ModuleHeader>()
        assertEquals(listOf("1/2", "0/1"), headers.map { it.counter })
        assertEquals(listOf(false, true), headers.map { it.isCollapsed })
        assertEquals(2, list(state).items.count { it is ListItemUi.Row })
    }

    @Test
    fun `プレビューに失敗した行はチェックできず、ファイル数は疑問符で、原因を見るメニューを持つ`() {
        val row = list(ready(arch to listOf(broken))).row(broken)
        assertEquals(RowLeadUi.Check(checked = false, enabled = false), row.lead)
        assertEquals(RowMarker.Blocked, row.marker)
        assertEquals("? files", row.trailing)
        assertTrue(row.menu.any { it.intent == KatachiIntent.ShowCause(id(broken)) })
    }

    @Test
    fun `チェックした行の下にフォームが開き、触っていない必須欄にはまだエラーを出さない`() {
        val form = list(ready(arch to listOf(repository)).then(check(repository))).row(repository).form()
        assertEquals(listOf("name", "withImpl", "implSuffix"), form.fields.map { it.id.parameterName })
        val name = form.field("name").cast<FieldUi.Text>()
        assertTrue(name.isRequired)
        assertNull(name.error)
    }

    @Test
    fun `一度入れて空にした必須欄には欄の下にエラーを出す`() {
        val state = ready(arch to listOf(repository)).then(check(repository), input(repository, "name", "User"), input(repository, "name", ""))
        assertEquals("入力してください", list(state).row(repository).form().field("name").cast<FieldUi.Text>().error)
    }

    @Test
    fun `分岐の外に出た欄は灰色の1行に畳み、分岐の説明を制御する欄に出す`() {
        val state = ready(arch to listOf(repository)).then(check(repository), input(repository, "name", "User"), input(repository, "withImpl", "false"))
        val form = list(state).row(repository).form()
        assertEquals(FieldUi.Collapsed(FieldId(id(repository), "implSuffix"), "implSuffix（withImpl がオンのとき）"), form.field("implSuffix"))
        assertEquals("オン: UserRepositoryImpl.kt を作る", form.field("withImpl").cast<FieldUi.Bool>().note)
        assertEquals("UserRepository.kt を生成", form.files.text)
    }

    @Test
    fun `未入力のプレースホルダはそのまま要約に出す`() {
        val form = list(ready(arch to listOf(service)).then(check(service))).row(service).form()
        assertEquals("\${name}Service.kt を生成", form.files.text)
    }

    @Test
    fun `同名同型の欄は連動の印を出し、別の値に書き換えた欄は連動を戻す操作を持つ`() {
        val useCase = template("domain/UseCase")
        val state = ready(arch to listOf(repository, service, useCase))
            .then(check(repository), check(service), check(useCase), input(repository, "name", "User"), input(service, "name", "Account"))
        val rows = list(state)
        assertEquals(LinkUi.Linked, rows.row(repository).form().field("name").cast<FieldUi.Text>().link)
        assertEquals(LinkUi.Linked, rows.row(useCase).form().field("name").cast<FieldUi.Text>().link)
        val serviceName = FieldId(id(service), "name")
        assertEquals(LinkUi.Unlinked(KatachiIntent.Relink(serviceName)), rows.row(service).form().field("name").cast<FieldUi.Text>().link)
    }

    @Test
    fun `押せない理由をフッターに出し、押すとその欄を開く対象を持つ`() {
        val footer = list(ready(arch to listOf(repository)).then(check(repository))).footer.cast<FooterUi.Form>()
        assertEquals("Repository: name が未入力です", footer.reason)
        assertEquals(FieldId(id(repository), "name"), footer.reasonTarget)
        assertEquals(false, footer.generateEnabled)
    }

    @Test
    fun `何もチェックしていないときは理由の行を出さずボタンのツールチップにだけ出す`() {
        val footer = list(ready(arch to listOf(repository))).footer.cast<FooterUi.Form>()
        assertNull(footer.reason)
        assertEquals("テンプレートにチェックを入れてください", footer.generateTooltip)
    }

    @Test
    fun `上書きを選ぶと注意を出す`() {
        val footer = list(ready(arch to listOf(service)).then(KatachiIntent.SetOnExisting(OnExistingChoice.Overwrite))).footer.cast<FooterUi.Form>()
        assertEquals("既存ファイルを確認なしで置き換えます", footer.overwriteWarning)
        assertEquals(2, footer.onExistingIndex)
    }

    @Test
    fun `検索に一致しないチェック中の行は検索外・選択中として畳んで残す`() {
        val state = ready(arch to listOf(repository, service)).then(check(repository), input(repository, "name", "User"), KatachiIntent.Search("Service"))
        val row = list(state).row(repository)
        assertEquals("検索外・選択中", row.note)
        assertNull(row.body)
    }

    @Test
    fun `押せない理由から開いた行は検索外でもフォームを出す`() {
        val state = ready(arch to listOf(repository, service))
            .then(check(repository), KatachiIntent.Search("Service"), KatachiIntent.RevealField(FieldId(id(repository), "name")))
        assertTrue(list(state).row(repository).body is RowBodyUi.Form)
    }

    @Test
    fun `検索に何も一致せずチェック中の行も無ければ検索の空状態を出す`() {
        val empty = list(ready(arch to listOf(service)).then(KatachiIntent.Search("zzz"))).emptySearch
        assertEquals("「zzz」に一致するテンプレートはありません", empty?.title)
        assertEquals(KatachiIntent.Search(""), empty?.actions?.single()?.intent)
    }

    @Test
    fun `同期が古いときのエラーは同期する導線を出す`() {
        val failure = LoadFailure.Gradle(GradleFailure.TaskNotFound(":arch:katachiInternalTemplatesJson", listOf("Task not found")))
        val body = ui(KatachiScreenState(phase = ScreenPhase.LoadError(failure, emptyList()))).body.cast<BodyUi.Error>()
        assertTrue(body.message.actions.any { it.intent == KatachiIntent.SyncGradle })
        assertEquals(false, body.message.details?.isOpen)
    }

    @Test
    fun `キャッシュがあるときの読み込み失敗と定義の変更と消えたテンプレートは帯で出す`() {
        val state = ready(arch to listOf(service)).copy(
            loadErrorBanner = LoadFailure.Cancelled,
            removedTemplates = listOf(id(repository)),
            view = ViewState(definitionChanged = true),
        )
        assertEquals(listOf(BannerKind.Error, BannerKind.Info, BannerKind.Warning), list(state).banners.map { it.kind })
        assertEquals("Repository は無くなりました", list(state).banners.last().text)
    }
}
