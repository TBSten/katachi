package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.EmptyReason
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.ScreenPhase

internal class KatachiToolWindowFactoryTest : KatachiIdeTestBase() {

    fun `test Tool Window を作ると Compose のタブが1つ載り、同期データから定義モジュールを見つけて読み込む`() {
        val viewModel = installService()
        val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)

        KatachiToolWindowFactory().createToolWindowContent(project, toolWindow)

        assertEquals(1, toolWindow.contentManager.contentCount)
        val state = viewModel.waitFor("loaded") { it.phase == ScreenPhase.Ready && it.loading == null }
        assertEquals(listOf(":arch-a"), state.modules.map { it.gradlePath })
        assertTrue(state.rows.any { it.template.roleName == "data/Repository" })
        assertEquals(listOf(":arch-a:${KatachiModule.TEMPLATES_JSON_TASK}"), gradle.requests.single().taskNames)
    }

    fun `test 定義モジュールが2つあれば1回の Gradle 実行に2つのタスクを並べる`() {
        synced = syncedWith(":arch-a", ":arch-b")
        val viewModel = installService()

        KatachiToolWindowFactory().createToolWindowContent(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))

        val state = viewModel.waitFor("loaded") { it.phase == ScreenPhase.Ready && it.loading == null }
        assertEquals(listOf(":arch-a", ":arch-b"), state.modules.map { it.gradlePath })
        assertEquals(
            listOf(":arch-a:${KatachiModule.TEMPLATES_JSON_TASK}", ":arch-b:${KatachiModule.TEMPLATES_JSON_TASK}"),
            gradle.requests.single().taskNames,
        )
    }

    fun `test 同期前なら Gradle を走らせずに空状態を出し、同期が終わると探し直す`() {
        synced = SyncedProject.NotSynced
        val viewModel = installService()
        KatachiToolWindowFactory().createToolWindowContent(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))
        viewModel.waitFor("empty") { it.phase == ScreenPhase.Empty(EmptyReason.NotSynced) }
        assertEquals(0, gradle.requests.size)

        synced = syncedWith(":arch-a")
        viewModel.dispatch(KatachiIntent.SyncCompleted)

        viewModel.waitFor("loaded after sync") { it.phase == ScreenPhase.Ready && it.loading == null }
    }

    fun `test Tool Window を開く前の同期完了では Gradle を走らせない`() {
        val viewModel = installService()

        viewModel.dispatch(KatachiIntent.SyncCompleted)

        assertEquals(ScreenPhase.Initializing, viewModel.state.value.phase)
        assertEquals(0, gradle.requests.size)
    }

    fun `test プラットフォームが作るサービスは Gradle をリンクしていないプロジェクトを Gradle でないと判定する`() {
        val viewModel = KatachiProjectService.getInstance(project).viewModel

        viewModel.dispatch(KatachiIntent.Opened)

        viewModel.waitFor("empty") { it.phase == ScreenPhase.Empty(EmptyReason.NotGradle) }
    }

    fun `test インデックス作成中でも Tool Window を作って読み込める`() {
        val viewModel = installService()

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            assertTrue(DumbService.isDumb(project))
            assertTrue(DumbService.isDumbAware(KatachiToolWindowFactory()))
            KatachiToolWindowFactory().createToolWindowContent(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))
            viewModel.waitFor("loaded while dumb") { it.phase == ScreenPhase.Ready && it.loading == null }
        }
    }
}
