package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.cast
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reloads, syncs and definition changes mixed into generations (spec 04, E-41, E-44, E-45). */
class ReloadScenarioTest {
    private val base = ContractFixtures.json("arch-a")

    /** arch-a with a required `entity` parameter added to Repository. */
    private val withEntity = base
        .replaceFirst("\"parameterNames\": [", "\"parameterNames\": [\"entity\", ")
        .replaceFirst(
            "\"parameters\": [",
            "\"parameters\": [{\"name\": \"entity\", \"kind\": \"StringParameter\", \"typeName\": \"String\", \"default\": null, " +
                "\"acceptedValues\": [], \"isRequired\": true, \"previewValue\": \"\${entity}\", \"previewValueSource\": \"Placeholder\"}, ",
        )

    /** arch-a with a template `misc/Added` at the end of the list. */
    private val withAdded = base
        .replaceFirst("\"templates\": [", "\"templates\": [{\"roleName\": \"misc/Added\", \"title\": null, \"summary\": null, \"parameterNames\": [], \"fileCount\": 1}, ")
        .replaceFirst(
            "\"details\": [",
            "\"details\": [{\"roleName\": \"misc/Added\", \"title\": null, \"summary\": null, \"parameters\": [], " +
                "\"files\": [{\"fileName\": \"Added.kt\", \"path\": \"Added.kt\", \"unresolvedPatterns\": [], \"content\": \"\"}], \"branches\": [], \"exampleCommand\": \"x\"}, ",
        )

    /** arch-a where `misc/Broken`'s preview works now. */
    private val brokenFixed = base
        .replace("\"fileCount\": null", "\"fileCount\": 1")
        .replaceFirst(
            "\"details\": [",
            "\"details\": [{\"roleName\": \"misc/Broken\", \"title\": null, \"summary\": null, \"parameters\": [{\"name\": \"name\", \"kind\": \"StringParameter\", " +
                "\"typeName\": \"String\", \"default\": null, \"acceptedValues\": [], \"isRequired\": true, \"previewValue\": \"\${name}\", \"previewValueSource\": \"Placeholder\"}], " +
                "\"files\": [{\"fileName\": \"\${name}Broken.kt\", \"path\": \"misc/\${name}Broken.kt\", \"unresolvedPatterns\": [], \"content\": \"\"}], \"branches\": [], \"exampleCommand\": \"x\"}, ",
        )

    private fun ScenarioHarness.fillRepository() {
        check(repository)
        input(repository, "name", "User")
    }

    @Test
    fun `結果を出している間に再読み込みしても結果は出したままで続けて生成すると新しい定義のフォームに戻る`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        s.generate()
        assertEquals(TitleAction.Reload, uiStateOf(s.state, JapaneseKatachiStrings, ScenarioHarness.NOW).titleAction)

        s.reload(withEntity)
        assertTrue(s.state.generation is GenerationState.Finished)
        assertEquals(RowLeadUi.Status(RowStatus.Done), s.row(s.repository).lead)

        s.dispatch(KatachiIntent.ContinueGenerating)
        assertEquals(listOf("entity", "name", "item", "withImpl", "implSuffix"), s.form(s.repository).fields.map { it.id.parameterName })
        s.input(s.repository, "name", "Order")
        assertEquals("Repository: entity が未入力です", s.formFooter().reason)
        s.input(s.repository, "entity", "Order")
        s.generate()
        assertEquals("Order", s.lastArgs()["entity"])
    }

    @Test
    fun `結果を出している間の同期完了では読み込み直さずそのまま続けて生成できる`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        s.generate()
        s.dispatch(KatachiIntent.SyncCompleted)
        s.settle()
        assertEquals(1, s.loads)
        s.dispatch(KatachiIntent.ContinueGenerating)
        s.input(s.repository, "name", "Order")
        s.generate()
        assertEquals(2, s.katachi.runs.size)
    }

    @Test
    fun `定義変更の帯は結果の画面でも出て再読み込みで消え次の生成を邪魔しない`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        s.generate()
        s.dispatch(KatachiIntent.DefinitionChanged)
        assertEquals(listOf("定義が変わりました。"), s.ui().banners.map { it.text })

        s.dispatch(KatachiIntent.ContinueGenerating)
        assertEquals(1, s.ui().banners.size)
        s.reload(base)
        assertTrue(s.ui().banners.isEmpty())
        s.input(s.repository, "name", "Order")
        s.generate()
        assertTrue(s.ui().banners.isEmpty())
    }

    @Test
    fun `生成中の再読み込みと二度目の生成は無視され読み込みも生成も1回ずつだけ走る`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        val gate = CompletableDeferred<Unit>()
        s.katachi.override = { _, _ -> FakeRun(ContractFixtures.outputLines("new", ROOT), gate = gate) }
        s.dispatch(KatachiIntent.Generate)
        s.runner.reachedGate.await()
        s.dispatch(KatachiIntent.Reload, KatachiIntent.Generate, KatachiIntent.SyncCompleted, KatachiIntent.Generate)
        s.settle()
        assertEquals(TitleAction.Reload, uiStateOf(s.state, JapaneseKatachiStrings, ScenarioHarness.NOW).titleAction)
        assertEquals("生成しています… 1 / 1", s.ui().footer.cast<FooterUi.Generating>().label)

        gate.complete(Unit)
        s.finished()
        s.settle()
        assertEquals(1, s.loads)
        assertEquals(1, s.katachi.runs.size)
    }

    @Test
    fun `読み込み中の再読み込みは無視され止めたあとは押し直せる`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.loadGate = CompletableDeferred()
        s.runner.reachedGate = CompletableDeferred()
        s.dispatch(KatachiIntent.Reload)
        s.runner.reachedGate.await()
        s.dispatch(KatachiIntent.Reload, KatachiIntent.Reload)
        s.settle()
        assertEquals(2, s.loads)
        assertEquals(TitleAction.Stop, uiStateOf(s.state, JapaneseKatachiStrings, ScenarioHarness.NOW).titleAction)

        s.dispatch(KatachiIntent.CancelLoad)
        s.await { it.loading == null }
        assertEquals(ScreenPhase.Ready, s.state.phase)
        s.loadGate = null
        s.reload(base)
        assertEquals(3, s.loads)
    }

    @Test
    fun `失敗した行が再読み込みで消えたら残りをやり直しても消えた行を持ち越さない`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        s.check(s.useCase)
        s.katachi.override = { args, _ ->
            if (args["roleName"] == "domain/UseCase") FakeRun(ContractFixtures.outputLines("unknown-arg", ROOT), GradleRunOutcome.Failed) else null
        }
        s.generate()
        s.reload(base.replace("\"domain/UseCase\"", "\"domain/Renamed\""))
        assertEquals(listOf(s.useCase), s.state.removedTemplates)
        assertFalse(s.rowIds().contains(s.useCase))

        s.dispatch(KatachiIntent.RetryRemaining)
        assertTrue(s.state.form.selected.isEmpty())
        assertTrue(s.state.form.expanded.isEmpty())
        assertEquals(false, s.formFooter().generateEnabled)
    }

    @Test
    fun `結果の画面で再読み込みして増えたテンプレートは結果の間は選べず戻ると選べる`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        s.generate()
        s.reload(withAdded)
        val added = TemplateId(s.arch.id, "misc/Added")
        assertEquals(RowLeadUi.Check(checked = false, enabled = false), s.row(added).lead)

        s.dispatch(KatachiIntent.UncheckAll)
        s.check(added)
        assertEquals(RowLeadUi.Check(checked = true, enabled = true), s.row(added).lead)
        s.generate()
        assertEquals(RowLeadUi.Status(RowStatus.Done), s.row(added).lead)
    }

    @Test
    fun `原因を見た壊れたテンプレートが再読み込みで直ったらチェックすると原因ではなくフォームを出す`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        val broken = TemplateId(s.arch.id, "misc/Broken")
        assertEquals(RowMarker.Blocked, s.row(broken).marker)
        s.dispatch(KatachiIntent.ShowCause(broken))
        s.await { it.view.causes[broken] is CauseState.Loaded }
        assertTrue(s.row(broken).body is RowBodyUi.Cause)

        s.reload(brokenFixed)
        assertEquals(RowMarker.None, s.row(broken).marker)
        s.check(broken)
        assertTrue(s.row(broken).body is RowBodyUi.Form)
        assertNull(s.state.view.causes[broken])
    }
}
