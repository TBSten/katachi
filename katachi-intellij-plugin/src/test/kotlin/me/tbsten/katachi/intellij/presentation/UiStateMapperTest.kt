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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UiStateMapperTest {
    private val now = Instant.parse("2026-09-27T10:00:00Z")
    private val arch = module(":arch")
    private val repository = template(
        "data.Repository",
        parameters = listOf(stringParam("name"), booleanParam("withImpl"), stringParam("implSuffix", default = "Impl")),
        files = listOf(file("\${name}Repository.kt"), file("\${name}Repository\${implSuffix}.kt")),
        branches = listOf(branch("withImpl", "false", removedFiles = listOf("\${name}Repository\${implSuffix}.kt"), removedParameters = listOf("implSuffix"))),
        title = "リポジトリ",
    )
    private val service = template("domain.Service", files = listOf(file("\${name}Service.kt")))
    private val broken = previewFailed("data.Cache")

    private fun ready(vararg modules: Pair<KatachiModule, List<TemplateModel>>) = KatachiScreenState(
        phase = ScreenPhase.Ready,
        modules = modules.map { it.first },
        snapshots = modules.map { (module, templates) -> DescriptionSnapshot(module, "0.3.0", templates, now.minusSeconds(600)) },
    )

    /** A project whose modules run katachi 0.2.x: templates only, no definition (design T-8). */
    private fun readyOn02(vararg modules: Pair<KatachiModule, List<TemplateModel>>) = ready(*modules).let { state ->
        state.copy(snapshots = state.snapshots.map { it.copy(katachiVersion = "0.2.1") })
    }

    private fun KatachiScreenState.then(vararg intents: KatachiIntent) = intents.fold(this) { s, i -> applyFormIntent(s, i)!! }

    private fun id(template: TemplateModel, module: KatachiModule = arch) = TemplateId(module.id, template.template)

    private fun check(template: TemplateModel) = KatachiIntent.ToggleCheck(id(template))

    private fun input(template: TemplateModel, name: String, value: String) = KatachiIntent.Input(FieldId(id(template), name), value)

    private fun ui(state: KatachiScreenState) = uiStateOf(state, JapaneseKatachiStrings, now)

    private fun list(state: KatachiScreenState): ListUi = (ui(state).body as? BodyUi.Listing)?.list ?: error("not a list: ${ui(state).body}")

    private fun ListUi.row(template: TemplateModel): TemplateRowUi =
        items.firstNotNullOf { (it as? ListItemUi.Row)?.row?.takeIf { row -> row.id.template == template.template } }

    private fun TemplateRowUi.form(): FormUi = (body as? RowBodyUi.Form)?.form ?: error("no form: $body")

    private fun FormUi.field(name: String): FieldUi = fields.first { it.id.parameterName == name }

    @Test
    fun `katachiが見つからずタスク一覧も無ければAndroid Studioの設定の場所を添える`() {
        fun body(reason: EmptyReason) = ((ui(KatachiScreenState(phase = ScreenPhase.Empty(reason))).body as? BodyUi.Empty)?.message ?: error("not empty")).body
        assertEquals(listOf(JapaneseKatachiStrings.notInstalledBody), body(EmptyReason.NotInstalled(taskListMissing = false)))
        assertEquals(
            listOf(JapaneseKatachiStrings.notInstalledBody, JapaneseKatachiStrings.taskListMissingHint),
            body(EmptyReason.NotInstalled(taskListMissing = true)),
        )
    }

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
    fun `プレビューに失敗した行はチェックできず、原因を見るメニューを持つ`() {
        val row = list(ready(arch to listOf(broken))).row(broken)
        assertEquals(RowLeadUi.Check(checked = false, enabled = false), row.lead)
        assertEquals(RowMarker.Blocked, row.marker)
        assertEquals("", row.trailing)
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
    fun `分岐の外に出た欄は灰色の1行に畳み、制御する欄には予想の説明を出さない`() {
        val state = ready(arch to listOf(repository)).then(check(repository), input(repository, "name", "User"), input(repository, "withImpl", "false"))
        val form = list(state).row(repository).form()
        assertEquals(FieldUi.Collapsed(FieldId(id(repository), "implSuffix"), "implSuffix（withImpl がオンのとき）"), form.field("implSuffix"))
        assertEquals(FieldUi.Bool(FieldId(id(repository), "withImpl"), "withImpl", checked = false, link = LinkUi.None), form.field("withImpl"))
    }

    @Test
    fun `行の右端に予想のファイル数を出さない`() {
        val state = ready(arch to listOf(repository, service)).then(check(repository), input(repository, "name", "User"))
        assertEquals("", list(state).row(repository).trailing)
        assertEquals("", list(state).row(service).trailing)
    }

    @Test
    fun `文字列の欄の複数行への切り替えは向きに合わせたツールチップを持つ`() {
        val state = ready(arch to listOf(service)).then(check(service))
        val field = FieldId(id(service), "name")
        assertEquals("複数行で入力", list(state).row(service).form().field("name").cast<FieldUi.Text>().multilineTooltip)
        val text = list(state.then(KatachiIntent.ToggleMultiline(field))).row(service).form().field("name").cast<FieldUi.Text>()
        assertEquals(true, text.isMultiline)
        assertEquals("1行に戻す", text.multilineTooltip)
    }

    @Test
    fun `同名同型の欄は連動の印を出し、別の値に書き換えた欄は連動を戻す操作を持つ`() {
        val useCase = template("domain.UseCase")
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
        assertEquals("リポジトリ: name が未入力です", footer.reason)
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
    fun `katachi が定義を読めなかったときの失敗は 専用の見出しと 誤りの文と 読み直す導線を出す`() {
        val failure = LoadFailure.Gradle(GradleFailure.DefinitionRejected(emptyList(), listOf("`.template { }` is attached to a wildcard.")))
        val message = ui(KatachiScreenState(phase = ScreenPhase.LoadError(failure, emptyList()))).body.cast<BodyUi.Error>().message

        assertEquals(listOf("katachi が定義を読めませんでした。", "誤りのある行は定義ファイルに下線で示しています。メッセージは詳細にあります。"), message.body)
        assertTrue(message.actions.any { it.intent == KatachiIntent.Reload })
        assertEquals(listOf("`.template { }` is attached to a wildcard."), message.details?.lines)
    }

    @Test
    fun `キャッシュがあるときの読み込み失敗と定義の変更と消えたテンプレートは帯で出す`() {
        val state = ready(arch to listOf(service)).copy(
            loadErrorBanner = LoadFailure.Cancelled,
            removedTemplates = listOf(id(repository)),
            view = ViewState(definitionChanged = true),
        )
        // `ready` is a templates-only (katachi 0.2.x) module, so the last band is T-8's.
        assertEquals(listOf(BannerKind.Error, BannerKind.Info, BannerKind.Warning, BannerKind.Info), list(state).banners.map { it.kind })
        assertEquals("Repository は定義から無くなりました（チェックを外しました）", list(state).banners[2].text)
        assertEquals(KatachiIntent.DismissTooOld, list(state).banners.last().dismiss)
    }

    // katachi 0.2.x (design T-8): a module with the templates task only. `ready` builds exactly that.
    private val tooOldBanner = { state: KatachiScreenState -> list(state).banners.firstOrNull { it.dismiss == KatachiIntent.DismissTooOld } }

    @Test
    fun `0_2 系の定義モジュールだけなら一覧の上に katachi 0_3 以降が必要という帯と更新手順を出す`() {
        val banner = tooOldBanner(readyOn02(arch to listOf(service))) ?: error("no band")

        assertEquals(BannerKind.Info, banner.kind)
        assertEquals("ガターと違反の表示には katachi 0.3 以降が必要です（いま 0.2.1）", banner.text)
        assertEquals(listOf(ActionUi("更新手順を見る", KatachiIntent.OpenDocs(DocsPage.Update))), banner.actions)
    }

    @Test
    fun `帯の版は英語の IDE では英語で、版が分からなければ括弧ごと省く`() {
        val unknown = ready(arch to listOf(service)).let { it.copy(snapshots = it.snapshots.map { snapshot -> snapshot.copy(katachiVersion = null) }) }
        val banner = list(unknown).banners.single { it.dismiss == KatachiIntent.DismissTooOld }
        val english = ((uiStateOf(unknown, EnglishKatachiStrings, now).body as BodyUi.Listing).list.banners).single { it.dismiss == KatachiIntent.DismissTooOld }

        assertEquals("ガターと違反の表示には katachi 0.3 以降が必要です", banner.text)
        assertEquals("The gutter and violation marks need katachi 0.3 or later", english.text)
    }

    @Test
    fun `帯の ✕ のあとは同じプロジェクトの間は出さない`() {
        val old = readyOn02(arch to listOf(service))
        assertNotNull(tooOldBanner(old))
        val dismissed = applyViewIntent(old.view, KatachiIntent.DismissTooOld) ?: error("not a view intent")

        assertNull(tooOldBanner(old.copy(view = dismissed)))
    }

    @Test
    fun `定義を読める 0_3 のモジュールが1つでもあれば帯を出さない`() {
        val current = arch.copy(capabilities = setOf(me.tbsten.katachi.intellij.model.KatachiCapability.Definition, me.tbsten.katachi.intellij.model.KatachiCapability.Templates))
        val state = ready(current to listOf(service), module(":legacy") to listOf(broken))
        val withDefinition = state.copy(
            snapshots = state.snapshots.map { snapshot ->
                if (snapshot.module.id == current.id) snapshot.copy(definition = me.tbsten.katachi.intellij.testing.definitionModel()) else snapshot
            },
        )

        assertNull(tooOldBanner(withDefinition))
    }
}
