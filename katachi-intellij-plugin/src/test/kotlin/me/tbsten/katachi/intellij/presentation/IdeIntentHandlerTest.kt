package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeIdeEffects
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeIntentHandlerTest {
    private val fs = FakeFileSystem()
    private val effects = FakeIdeEffects()
    private val arch = module(":arch-a")
    private val repository = TemplateId(arch.id, "data/Repository")
    private val synced = SyncedProject.Synced(
        listOf(SyncedRoot(ROOT, "project", listOf(SyncedModule(":arch-a", arch.directory, setOf(KatachiModule.TEMPLATES_JSON_TASK), "0.3.0")))),
    )
    private val runner = FakeGradleTaskRunner(fs) { request, _ ->
        if (request.taskNames.first().endsWith(KatachiModule.TEMPLATES_JSON_TASK)) {
            FakeRun(writes = mapOf(arch.templateDescriptionJson to ContractFixtures.json("arch-a")))
        } else {
            FakeRun(
                lines = listOf("> Task :arch-a:katachiTemplates", "", "[FAILED] template", "  Parameter `name` is missing", "", "BUILD FAILED in 1s"),
                tasks = listOf(":arch-a:katachiTemplates"),
            )
        }
    }

    private suspend fun CoroutineScope.loaded(): KatachiToolWindowViewModel {
        val viewModel = KatachiToolWindowViewModel(this, { synced }, runner, fs, effects)
        viewModel.dispatch(KatachiIntent.Opened)
        withTimeout(5_000) { viewModel.state.first { it.phase == ScreenPhase.Ready && it.loading == null } }
        return viewModel
    }

    @Test
    fun `コマンドをコピーは入力値を反映した gradlew の行をクリップボードに置く`() = runBlocking {
        val viewModel = loaded()
        viewModel.dispatch(KatachiIntent.Input(FieldId(repository, "name"), "User"))

        viewModel.dispatch(KatachiIntent.CopyCommand(repository))

        val command = effects.clipboard.single()
        assertTrue(command, command.startsWith("./gradlew :arch-a:katachiTemplate --arg roleName=data/Repository"))
        assertTrue(command, "--arg name=User" in command)
        assertTrue(command, "--arg onExisting=fail" in command)
    }

    @Test
    fun `原因を見るは katachiTemplates を roleName つきで走らせ、Gradle の行を除いた本文を行の下に出す`() = runBlocking {
        val viewModel = loaded()

        viewModel.dispatch(KatachiIntent.ShowCause(repository))

        val state = withTimeout(5_000) { viewModel.state.first { it.view.causes[repository] is CauseState.Loaded } }
        assertEquals(CauseState.Loaded(listOf("[FAILED] template", "  Parameter `name` is missing")), state.view.causes[repository])
        assertEquals(listOf(":arch-a:katachiTemplates", "--arg", "roleName=data/Repository"), runner.requests.last().taskNames)
    }

    @Test
    fun `ログ・同期・ドキュメントは IDE にそのまま渡す`() = runBlocking {
        val viewModel = loaded()

        viewModel.dispatch(KatachiIntent.ShowLog)
        viewModel.dispatch(KatachiIntent.SyncGradle)
        viewModel.dispatch(KatachiIntent.OpenDocs(DocsPage.Install))

        assertEquals(listOf("showLog", "sync", "docs:Install"), effects.log.filter { it == "showLog" || it == "sync" || it.startsWith("docs:") })
    }

    @Test
    fun `定義が変わったら帯を出し、読み込みが終わると消す`() = runBlocking {
        val viewModel = loaded()

        viewModel.dispatch(KatachiIntent.DefinitionChanged)
        assertTrue(viewModel.state.value.view.definitionChanged)

        viewModel.dispatch(KatachiIntent.Reload)
        withTimeout(5_000) { viewModel.state.first { it.loading == null && !it.view.definitionChanged } }
        Unit
    }
}
