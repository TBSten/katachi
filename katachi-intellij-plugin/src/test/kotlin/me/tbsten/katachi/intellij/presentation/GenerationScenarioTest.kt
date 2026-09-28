package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.cast
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generations run back to back: what one run leaves (results, inputs, files on disk, open parts
 * of the screen) must reach the next one only where the spec says so (spec 02 state diagram,
 * spec 03 "生成結果").
 */
class GenerationScenarioTest {
    private fun ScenarioHarness.fillRepository(name: String = "User") {
        check(repository)
        input(repository, "name", name)
    }

    private fun ScenarioHarness.resultFileNames(id: TemplateId) = result(id).files.map { "${it.name} ${it.badge} ${it.note.orEmpty()}".trim() }

    @Test
    fun `続けて生成で同じ名前にすると2回目は1回目に書いたファイルと衝突しダイアログは実際の出力の既存ファイルを出す`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()

        s.generate()
        assertEquals(listOf("UserRepository.kt 新規 ← Opened", "UserRepositoryImpl.kt 新規"), s.resultFileNames(s.repository))
        assertEquals("1 件を生成しました", s.resultFooter().summary)
        assertEquals(listOf(s.dataDir.resolve("UserRepository.kt")), s.effects.opened)

        s.dispatch(KatachiIntent.ContinueGenerating)
        assertNull(s.state.generation)
        assertEquals("", s.textField(s.repository, "name").value)
        assertEquals(false, s.formFooter().generateEnabled)
        assertEquals("リポジトリ: name が未入力です", s.formFooter().reason)

        s.input(s.repository, "name", "User")

        val questions = mutableListOf<ConflictQuestion>()
        s.effects.conflictAnswer = { questions += it; ConflictChoice.Stop }
        s.generate()
        assertEquals(listOf(s.dataDir.resolve("UserRepository.kt"), s.dataDir.resolve("UserRepositoryImpl.kt")), questions.single().existing)
        assertEquals("fail", s.lastArgs()["onExisting"])
        assertEquals(RowLeadUi.Status(RowStatus.Stopped), s.row(s.repository).lead)
        assertEquals(listOf("UserRepository.kt 既にある", "UserRepositoryImpl.kt 既にある"), s.resultFileNames(s.repository))
        assertEquals("1 件中 0 件を生成（何も書いていません）", s.resultFooter().summary)
        // Nothing new was written, so nothing new opens.
        assertEquals(1, s.effects.opened.size)
    }

    @Test
    fun `続けて生成で別の名前にすると新しいファイルだけを書いて開き前回の結果は出さない`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        s.input(s.repository, "withImpl", "false")
        s.generate()
        assertEquals(listOf("UserRepository.kt 新規 ← Opened"), s.resultFileNames(s.repository))

        s.dispatch(KatachiIntent.ContinueGenerating)
        // Booleans stay, so the Impl is still left out.
        assertEquals("false", s.inputOf(s.repository, "withImpl"))
        s.input(s.repository, "name", "Order")

        s.generate()
        assertEquals(listOf("OrderRepository.kt 新規 ← Opened"), s.resultFileNames(s.repository))
        assertEquals(listOf("UserRepository.kt", "OrderRepository.kt"), s.effects.opened.map { it.fileName.toString() })
        assertEquals(1, s.state.generation.cast<GenerationState.Finished>().report.items.size)
    }

    @Test
    fun `チェックを外して別のテンプレートで生成すると前の行は凍った一覧の行に戻り入力も残らない`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        s.dispatch(KatachiIntent.SetOnExisting(OnExistingChoice.Skip))
        s.generate()

        s.dispatch(KatachiIntent.UncheckAll)
        assertTrue(s.state.form.selected.isEmpty())
        assertTrue(s.state.form.inputs.isEmpty())
        assertEquals(OnExistingChoice.Skip, s.state.form.onExisting)
        assertEquals(RowLeadUi.Check(checked = false, enabled = true), s.row(s.repository).lead)
        assertNull(s.row(s.repository).body)
        assertEquals(false, s.formFooter().generateEnabled)

        s.check(s.useCase)
        s.input(s.useCase, "name", "Get")
        s.generate()
        assertEquals(listOf("template" to "domain.UseCase"), s.lastArgs().filterKeys { it == "template" }.toList())
        assertEquals(listOf(s.useCase), s.state.generation.cast<GenerationState.Finished>().report.templateIds)
        assertEquals(RowLeadUi.Check(checked = false, enabled = false), s.row(s.repository).lead)
        assertNull(s.row(s.repository).body)
        assertEquals(listOf("GetUseCase.kt 新規 ← Opened"), s.resultFileNames(s.useCase))

        // Checking Repository again after "uncheck all" starts from an empty form.
        s.dispatch(KatachiIntent.UncheckAll)
        s.check(s.repository)
        assertEquals("", s.textField(s.repository, "name").value)
    }

    @Test
    fun `途中で失敗して残りをやり直し成功したあと続けてもう一度生成できる`() = runBlocking {
        val s = ScenarioHarness(this)
        // A row of a second module (design draft section 6): same-module rows now run together in
        // one build, so "one item succeeds, the other fails" needs two separate modules.
        val archB = module(":arch-b")
        s.addModule(archB, ContractFixtures.json("arch-b"))
        s.open()
        s.fillRepository()
        val archBRepository = TemplateId(archB.id, "data.Repository")
        s.check(archBRepository)
        s.input(archBRepository, "name", "3")
        s.katachi.override = { args, index ->
            if (index == 1) FakeRun(ContractFixtures.outputLines("unknown-arg", ROOT), GradleRunOutcome.Failed) else null
        }
        s.generate()
        assertEquals(RowLeadUi.Status(RowStatus.Done), s.row(s.repository).lead)
        assertEquals(RowLeadUi.Status(RowStatus.Failed), s.row(archBRepository).lead)
        assertEquals(listOf("残りをやり直す", "続けて生成", "チェックを外す"), s.resultFooter().actions.map { it.label })

        s.dispatch(KatachiIntent.RetryRemaining)
        assertEquals(listOf(archBRepository), s.state.form.selected)
        assertEquals(RowLeadUi.Check(checked = false, enabled = true), s.row(s.repository).lead)
        assertEquals("3", s.textField(archBRepository, "name").value)
        assertEquals(true, s.formFooter().generateEnabled)

        s.generate()
        assertEquals(listOf(archBRepository), s.state.generation.cast<GenerationState.Finished>().report.templateIds)
        assertEquals("1 件を生成しました", s.resultFooter().summary)
        assertFalse(s.resultFooter().actions.any { it.label == "残りをやり直す" })

        s.dispatch(KatachiIntent.ContinueGenerating)
        s.input(archBRepository, "name", "5")
        s.generate()
        assertEquals("5", s.lastArgs()["name"])
        assertEquals(listOf("Repository5.kt 新規 ← Opened"), s.resultFileNames(archBRepository))
    }

    @Test
    fun `1件目で失敗して開いた詳細は直して生成し直した次の失敗には開いたまま持ち越さない`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository(name = "User/Admin")
        s.katachi.override = { _, _ -> FakeRun(ContractFixtures.outputLines("slash-in-name", ROOT), GradleRunOutcome.Failed) }
        s.generate()
        val key = DetailsKey.ResultRow(s.repository)
        s.dispatch(KatachiIntent.ToggleDetails(key))
        assertEquals(true, s.result(s.repository).details?.isOpen)

        s.dispatch(KatachiIntent.RetryRemaining)
        assertEquals("User/Admin", s.textField(s.repository, "name").value)
        s.input(s.repository, "name", "User")
        s.generate()
        assertEquals(RowLeadUi.Status(RowStatus.Failed), s.row(s.repository).lead)
        assertEquals(false, s.result(s.repository).details?.isOpen)
    }

    @Test
    fun `キャンセルしたあと残りをやり直すと新しいセッションで最後まで走り中断は持ち越さない`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        val gate = CompletableDeferred<Unit>()
        s.katachi.override = { _, index -> if (index == 0) FakeRun(gate = gate) else null }
        s.dispatch(KatachiIntent.Generate)
        s.runner.reachedGate.await()
        s.dispatch(KatachiIntent.CancelGeneration)
        s.finished()
        assertEquals(RowLeadUi.Status(RowStatus.Interrupted), s.row(s.repository).lead)
        assertEquals(1, s.runner.cancelled)

        s.dispatch(KatachiIntent.RetryRemaining)
        assertEquals(listOf(s.repository), s.state.form.selected)
        assertEquals(true, s.formFooter().generateEnabled)
        val second = s.generate()
        assertTrue(second.report.items.single().result is GenerationItemResult.Generated)
        assertEquals(RowLeadUi.Status(RowStatus.Done), s.row(s.repository).lead)
        assertEquals(1, s.runner.cancelled)
        assertEquals(2, s.katachi.runs.size)
    }

    @Test
    fun `読み込みを待つ生成を止めてすぐ押し直すと読み込みのあとに1回だけ生成する`() = runBlocking {
        val s = ScenarioHarness(this)
        s.fs.write(s.arch.templateDescriptionJson, ContractFixtures.json("arch-a"))
        s.loadGate = CompletableDeferred()
        s.dispatch(KatachiIntent.Opened)
        s.runner.reachedGate.await()
        s.fillRepository()
        s.dispatch(KatachiIntent.Generate, KatachiIntent.CancelGeneration, KatachiIntent.Generate)
        assertEquals("読み込みの完了を待っています…", s.ui().footer.cast<FooterUi.Generating>().label)

        s.loadGate?.complete(Unit)
        s.finished()
        s.settle()
        assertEquals(1, s.katachi.runs.size)
        assertEquals(RowLeadUi.Status(RowStatus.Done), s.row(s.repository).lead)
    }

    @Test
    fun `衝突で書かずに次へを選んだあと続けて同じ名前で生成するとまた聞かれる`() = runBlocking {
        val s = ScenarioHarness(this)
        s.fs.write(ROOT.resolve("data/src/main/kotlin/com/example/data/UserRepository.kt"), "// mine")
        s.open()
        s.fillRepository()
        var seen: KatachiScreenState? = null
        s.effects.conflictAnswer = {
            seen = s.state
            ConflictChoice.SkipAndContinue
        }
        s.generate()
        val running = seen?.generation.cast<GenerationState.Running>()
        assertEquals(GenerationRowStatus.AwaitingConflict, running.statuses[s.repository])
        assertEquals(RowLeadUi.Status(RowStatus.Skipped), s.row(s.repository).lead)
        assertEquals(listOf("UserRepository.kt スキップ"), s.resultFileNames(s.repository))
        assertTrue(s.effects.opened.isEmpty())

        s.dispatch(KatachiIntent.ContinueGenerating)
        s.input(s.repository, "name", "User")
        s.generate()
        assertEquals(2, s.effects.log.count { it == "conflict" })
        assertEquals(2, s.katachi.runs.size)
    }

    @Test
    fun `衝突で上書きしたあと続けて同じ名前で生成すると上書きしたファイルともまた衝突する`() = runBlocking {
        val s = ScenarioHarness(this)
        s.fs.write(ROOT.resolve("data/src/main/kotlin/com/example/data/UserRepository.kt"), "// mine")
        s.open()
        s.fillRepository()
        val questions = mutableListOf<ConflictQuestion>()
        s.effects.conflictAnswer = { questions += it; ConflictChoice.Overwrite }
        s.generate()
        assertEquals(listOf("UserRepository.kt 上書き ← Opened", "UserRepositoryImpl.kt 新規"), s.resultFileNames(s.repository))

        s.dispatch(KatachiIntent.ContinueGenerating)
        s.input(s.repository, "name", "User")
        s.generate()
        assertEquals(listOf(1, 2), questions.map { it.existing.size })
        assertEquals(listOf("UserRepository.kt 上書き ← Opened", "UserRepositoryImpl.kt 上書き"), s.resultFileNames(s.repository))
    }

    @Test
    fun `衝突でここで止めたあと残りをやり直すと同じ行がもう一度衝突を聞き上書きできる`() = runBlocking {
        val s = ScenarioHarness(this)
        // A row of a second module (design draft section 6): same-module rows now run together, so
        // "one item stops at a conflict, the other is deferred without one" needs two modules.
        val archB = module(":arch-b")
        s.addModule(archB, ContractFixtures.json("arch-b"))
        s.fs.write(ROOT.resolve("data/src/main/kotlin/com/example/data/UserRepository.kt"), "// mine")
        s.open()
        s.fillRepository()
        val archBRepository = TemplateId(archB.id, "data.Repository")
        s.check(archBRepository)
        s.input(archBRepository, "name", "3")
        val answers = ArrayDeque(listOf(ConflictChoice.Stop, ConflictChoice.Overwrite))
        s.effects.conflictAnswer = { answers.removeFirst() }
        s.generate()
        assertEquals(RowLeadUi.Status(RowStatus.Stopped), s.row(s.repository).lead)
        assertEquals(RowLeadUi.Status(RowStatus.NotRun), s.row(archBRepository).lead)

        s.dispatch(KatachiIntent.RetryRemaining)
        assertEquals(setOf(s.repository, archBRepository), s.state.form.selected.toSet())
        s.generate()
        assertEquals(RowLeadUi.Status(RowStatus.Done), s.row(s.repository).lead)
        assertEquals(RowLeadUi.Status(RowStatus.Done), s.row(archBRepository).lead)
        assertEquals("2 件を生成しました", s.resultFooter().summary)
        assertTrue(answers.isEmpty())
    }

    @Test
    fun `既存ファイルの扱いを途中で変えると次の生成はその扱いで走り結果の画面では変えられない`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        s.generate()
        s.dispatch(KatachiIntent.SetOnExisting(OnExistingChoice.Overwrite))
        assertEquals(OnExistingChoice.Fail, s.state.form.onExisting)

        s.dispatch(KatachiIntent.ContinueGenerating, KatachiIntent.SetOnExisting(OnExistingChoice.Skip))
        s.input(s.repository, "name", "User")
        assertNull(s.formFooter().overwriteWarning)
        s.generate()
        assertEquals("skip", s.lastArgs()["onExisting"])
        assertEquals(RowLeadUi.Status(RowStatus.Skipped), s.row(s.repository).lead)
        assertEquals(listOf("UserRepository.kt スキップ", "UserRepositoryImpl.kt スキップ"), s.resultFileNames(s.repository))

        s.dispatch(KatachiIntent.ContinueGenerating, KatachiIntent.SetOnExisting(OnExistingChoice.Overwrite))
        s.input(s.repository, "name", "User")
        assertEquals(2, s.formFooter().onExistingIndex)
        assertEquals("既存ファイルを確認なしで置き換えます", s.formFooter().overwriteWarning)
        s.generate()
        assertEquals("overwrite", s.lastArgs()["onExisting"])
        assertEquals(listOf("UserRepository.kt 上書き ← Opened", "UserRepositoryImpl.kt 上書き"), s.resultFileNames(s.repository))
        assertEquals(0, s.effects.log.count { it == "conflict" })
    }

    @Test
    fun `開かない設定なら結果に開いた印を付けず全部開く設定なら書いたファイル全部に付ける`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        s.effects.openSetting = OpenAfterGeneration.None
        s.generate()
        assertTrue(s.effects.opened.isEmpty())
        assertEquals(listOf("UserRepository.kt 新規", "UserRepositoryImpl.kt 新規"), s.resultFileNames(s.repository))

        s.dispatch(KatachiIntent.ContinueGenerating)
        s.input(s.repository, "name", "Order")
        s.effects.openSetting = OpenAfterGeneration.All
        s.generate()
        assertEquals(listOf("OrderRepository.kt 新規 ← Opened", "OrderRepositoryImpl.kt 新規 ← Opened"), s.resultFileNames(s.repository))
    }

    @Test
    fun `2回目の生成は前回の書き込みではなく今回の予想パスを自分の書き込みとして扱う`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.fillRepository()
        s.generate()
        s.dispatch(KatachiIntent.ContinueGenerating)
        s.input(s.repository, "name", "Order")
        s.generate()
        assertTrue(s.vm.isOwnWrite(s.dataDir.resolve("OrderRepository.kt")))
        assertFalse(s.vm.isOwnWrite(s.arch.directory.resolve("src/test/kotlin/Architecture.kt")))
    }
}
