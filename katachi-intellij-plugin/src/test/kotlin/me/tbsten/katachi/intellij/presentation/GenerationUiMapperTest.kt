package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GeneratedFile
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemReport
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.WrittenKind
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.cast
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GenerationUiMapperTest {
    private val now = Instant.parse("2026-09-27T10:00:00Z")
    private val arch = module(":arch")
    private val repository = template("data.Repository", title = "リポジトリ")
    private val service = template("domain.Service")
    private val useCase = template("domain.UseCase")
    private val ids = listOf(repository, service, useCase).map { TemplateId(arch.id, it.template) }
    private val repositoryId = ids[0]
    private val serviceId = ids[1]
    private val useCaseId = ids[2]

    private val base: KatachiScreenState = listOf(repository, service, useCase).fold(
        KatachiScreenState(
            phase = ScreenPhase.Ready,
            modules = listOf(arch),
            snapshots = listOf(DescriptionSnapshot(arch, "0.3.0", listOf(repository, service, useCase), now)),
        ),
    ) { state, template -> applyFormIntent(state, KatachiIntent.ToggleCheck(TemplateId(arch.id, template.template)))!! }
        .let { applyFormIntent(it, KatachiIntent.Input(FieldId(repositoryId, "name"), "User"))!! }

    private fun list(state: KatachiScreenState): ListUi = uiStateOf(state, JapaneseKatachiStrings, now).body.cast<BodyUi.Listing>().list

    private fun ListUi.row(id: TemplateId): TemplateRowUi = items.firstNotNullOf { (it as? ListItemUi.Row)?.row?.takeIf { row -> row.id == id } }

    private val written = GeneratedFile(ROOT.resolve("data/UserRepository.kt"), WrittenKind.New)
    private val overwritten = GeneratedFile(ROOT.resolve("data/UserRepositoryImpl.kt"), WrittenKind.Overwritten)

    /** Opened as the default setting does: the first written file. */
    private fun finished(vararg results: GenerationItemResult): KatachiScreenState {
        val report = GenerationReport(ids.zip(results.toList()) { id, r -> GenerationItemReport(listOf(id), r) })
        return base.copy(
            generation = GenerationState.Finished(report, "katachi: 生成前（Repository, Service, UseCase）", report.writtenFiles.take(1).map { it.path }),
        )
    }

    @Test
    fun `生成中は各行の状態を出し、実行中の行の下にタスクと送る引数を出す`() {
        val state = base.copy(
            generation = GenerationState.Running(
                ids,
                mapOf(
                    repositoryId to GenerationRowStatus.Running(":arch:katachiTemplate"),
                    serviceId to GenerationRowStatus.Waiting,
                    useCaseId to GenerationRowStatus.Waiting,
                ),
            ),
        )
        val rows = list(state)
        val running = rows.row(repositoryId)
        assertEquals(RowLeadUi.Status(RowStatus.Running), running.lead)
        assertEquals(RowBodyUi.Running("› :arch:katachiTemplate", "name = User"), running.body)
        assertEquals("待ち", rows.row(serviceId).trailing)
        assertEquals("生成しています… 1 / 3", rows.footer.cast<FooterUi.Generating>().label)
        assertEquals(false, rows.search.enabled)
    }

    @Test
    fun `衝突ダイアログを開いている間は対象の行を確認待ちにし、フッターは確認を待つ`() {
        val state = base.copy(
            generation = GenerationState.Running(
                ids,
                mapOf(repositoryId to GenerationRowStatus.AwaitingConflict),
                conflict = ConflictQuestion(listOf(repositoryId), 1, 3, listOf(written.path)),
            ),
        )
        val rows = list(state)
        assertEquals("既にあるファイル · 確認待ち", rows.row(repositoryId).trailing)
        assertEquals("確認を待っています…", rows.footer.cast<FooterUi.Generating>().label)
    }

    @Test
    fun `全件成功の結果は書いたファイルと種類を出し、最初のファイルに開いた印を付け、やり直しは出さない`() {
        val state = finished(
            GenerationItemResult.Generated(listOf(written, overwritten)),
            GenerationItemResult.Generated(emptyList(), writesUnknown = true),
            GenerationItemResult.Skipped(emptyList()),
        )
        val rows = list(state)
        val result = rows.row(repositoryId).body.cast<RowBodyUi.Result>().result
        assertEquals(listOf("新規", "上書き"), result.files.map { it.badge })
        assertEquals("← Opened", result.files.first().note)
        assertNull(result.files.last().note)
        assertEquals(listOf("生成済み（書いたファイルは不明）"), rows.row(serviceId).body.cast<RowBodyUi.Result>().result.message)
        val footer = rows.footer.cast<FooterUi.Result>()
        assertEquals("戻す: Local History「katachi: 生成前（Repository, Service, UseCase）」", footer.undo)
        assertTrue(footer.actions.none { it.intent == KatachiIntent.RetryRemaining })
    }

    @Test
    fun `途中で止まった結果は何件中何件かと書いたファイルが残ることを出し、失敗の行に本文と詳細を出す`() {
        val failure = GenerationItemResult.Failed(GenerationFailure.Katachi(listOf("ファイル名に使えない文字「/」", "詳しく")), listOf("> Task", "[FAILED] template"))
        val state = finished(GenerationItemResult.Generated(listOf(written)), failure, GenerationItemResult.NotRun)
        val rows = list(state)
        val failed = rows.row(serviceId).body.cast<RowBodyUi.Result>().result
        assertEquals(listOf("ファイル名に使えない文字「/」"), failed.message)
        assertEquals(KatachiIntent.ToggleDetails(DetailsKey.ResultRow(serviceId)), failed.details?.toggle)
        assertEquals(RowLeadUi.Status(RowStatus.NotRun), rows.row(useCaseId).lead)
        val footer = rows.footer.cast<FooterUi.Result>()
        assertEquals("3 件中 1 件を生成（書いたファイルは残る）", footer.summary)
        assertEquals(KatachiIntent.RetryRemaining, footer.actions.first().intent)
    }

    @Test
    fun `1件目で失敗して何も書いていないときはそう伝える`() {
        val failure = GenerationItemResult.Failed(GenerationFailure.Katachi(listOf("失敗")), emptyList())
        val footer = list(finished(failure, GenerationItemResult.NotRun, GenerationItemResult.NotRun)).footer.cast<FooterUi.Result>()
        assertEquals("3 件中 0 件を生成（何も書いていません）", footer.summary)
    }

    @Test
    fun `中断した行は書かれたかは不明と出し、何も書いていないとは言わない`() {
        val state = finished(GenerationItemResult.Interrupted(listOf(written.path)), GenerationItemResult.NotRun, GenerationItemResult.NotRun)
        val rows = list(state)
        assertEquals(listOf("中断（書かれたかは不明）"), rows.row(repositoryId).body.cast<RowBodyUi.Result>().result.message)
        assertEquals("3 件中 0 件を生成（書いたファイルは残る）", rows.footer.cast<FooterUi.Result>().summary)
    }

    @Test
    fun `生成中に書き終えた行の件数は一覧の件数と同じく files で数える`() {
        val state = base.copy(
            generation = GenerationState.Running(
                ids,
                mapOf(
                    repositoryId to GenerationRowStatus.Finished(GenerationItemResult.Generated(listOf(written, overwritten))),
                    serviceId to GenerationRowStatus.Finished(GenerationItemResult.Generated(listOf(written))),
                ),
            ),
        )
        val rows = list(state)
        assertEquals("2 files", rows.row(repositoryId).trailing)
        assertEquals("1 file", rows.row(serviceId).trailing)
    }

    @Test
    fun `IDE の例外で生成が途切れたら終わった行を残し次の行を失敗にして残りは未実行の結果にする`() {
        val running = base.copy(
            generation = GenerationState.Running(
                ids,
                mapOf(
                    repositoryId to GenerationRowStatus.Finished(GenerationItemResult.Generated(listOf(written))),
                    serviceId to GenerationRowStatus.Running(null),
                    useCaseId to GenerationRowStatus.Waiting,
                ),
            ),
        )
        val failure = GenerationFailure.NotReached(GradleFailure.Other(listOf("IllegalStateException: boom")))

        val report = abortGeneration(running, failure, "label").generation.cast<GenerationState.Finished>().report

        assertTrue(report.items[0].result is GenerationItemResult.Generated)
        assertEquals(GenerationItemResult.Failed(failure, emptyList()), report.items[1].result)
        assertEquals(GenerationItemResult.NotRun, report.items[2].result)
    }
}
