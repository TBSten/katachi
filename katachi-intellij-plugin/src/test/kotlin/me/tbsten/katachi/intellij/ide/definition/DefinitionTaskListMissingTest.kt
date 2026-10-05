package me.tbsten.katachi.intellij.ide.definition

import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.KatachiCapability
import me.tbsten.katachi.intellij.model.KatachiModule

/**
 * A sync without a task list (Android Studio's default, E-30, design 4.1): the candidates' tasks are unknown until
 * a load finds out. katachi 0.2.x (the templates task only) is told from 0.3 by what answered, and what answered
 * is remembered, so a load does not try the task that is not there again.
 */
internal class DefinitionTaskListMissingTest : DefinitionServiceTestBase() {
    override fun setUp() {
        super.setUp()
        synced = SyncedProject.Synced(listOf(SyncedRoot(root, "project", listOf(SyncedModule(":arch", moduleDirOf(":arch"), emptySet(), "0.3.0")))))
    }

    private fun taskNames(): List<String> = gradle.loadRequests.map { it.tasks.single().taskPath.substringAfterLast(':') }

    private fun awaitLoads(count: Int) {
        PlatformTestUtil.waitWithEventsDispatching(
            { "loads ${taskNames()}, running ${projectService.viewModel.isLoadRunning}" },
            { gradle.loadRequests.size >= count && !projectService.viewModel.isLoadRunning },
            10,
        )
    }

    fun `test 0_2 系はタスク一覧が無くても KatachiTooOld になり次の読み込みはテンプレートのタスクだけを試す`() {
        gradle.withoutDefinitionTask += "arch"
        gradle.fixtureOf["arch"] = "arch-a"
        install()

        definition.ensureLoaded()
        val tooOld = waitState("too old") { it is DefinitionState.Unavailable }
        assertEquals(DefinitionUnavailableReason.KatachiTooOld, (tooOld as DefinitionState.Unavailable).reason)
        awaitLoads(2)
        assertEquals(listOf(KatachiModule.DEFINITION_JSON_TASK, KatachiModule.TEMPLATES_JSON_TASK), taskNames())
        assertEquals(setOf(KatachiCapability.Templates), projectService.viewModel.definitionModules.single().capabilities)

        definition.refresh(RefreshReason.UserAction)
        awaitLoads(3)
        // Only the templates task: the definition task is known not to be there.
        assertEquals(KatachiModule.TEMPLATES_JSON_TASK, taskNames().last())
        assertEquals(3, taskNames().size)
        assertEquals(DefinitionUnavailableReason.KatachiTooOld, (definition.state.value as DefinitionState.Unavailable).reason)

        // A sync that changed nothing: the candidate is the same module, so nothing runs and the verdict stays.
        definition.refresh(RefreshReason.SyncCompleted)
        val until = System.currentTimeMillis() + 800
        PlatformTestUtil.waitWithEventsDispatching({ "idle" }, { System.currentTimeMillis() >= until }, 10)
        assertEquals(3, taskNames().size)
        assertEquals(DefinitionUnavailableReason.KatachiTooOld, (definition.state.value as DefinitionState.Unavailable).reason)
    }

    fun `test 0_3 はタスク一覧が無くても定義が読めて次の読み込みは定義のタスクだけを試す`() {
        install()

        val ready = loaded()
        assertEquals(1, ready.definitions.size)
        assertEquals(listOf(KatachiModule.DEFINITION_JSON_TASK), taskNames())
        assertEquals(setOf(KatachiCapability.Definition), projectService.viewModel.definitionModules.single().capabilities)

        definition.refresh(RefreshReason.UserAction)
        awaitLoads(2)
        assertEquals(listOf(KatachiModule.DEFINITION_JSON_TASK, KatachiModule.DEFINITION_JSON_TASK), taskNames())
        assertTrue(definition.state.value is DefinitionState.Ready)
    }
}
