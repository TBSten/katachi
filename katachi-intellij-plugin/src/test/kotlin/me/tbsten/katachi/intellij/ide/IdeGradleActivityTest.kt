package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * U-12: the project service sees the IDE's Gradle syncs and builds through ExternalSystem's progress
 * notifications (found with `javap`: `ExternalSystemProgressNotificationManager.addNotificationListener(listener, disposable)`),
 * played here through the platform's own notification manager, as ExternalSystem itself calls it.
 */
internal class IdeGradleActivityTest : KatachiIdeTestBase() {
    private val manager get() = ExternalSystemProgressNotificationManager.getInstance() as ExternalSystemProgressNotificationManagerImpl

    private fun waitBusy(service: KatachiProjectService, busy: Boolean) =
        PlatformTestUtil.waitWithEventsDispatching({ "busy is ${service.gradleActivity.busy.value}" }, { service.gradleActivity.busy.value == busy }, 10)

    fun `test IDE の同期とタスクの始まりから終わりまで Gradle が走っている`() {
        val service = installProjectService()
        val sync = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, project)
        val build = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
        assertFalse(service.gradleActivity.busy.value)

        manager.onStart(root.toString(), sync)
        waitBusy(service, true)
        manager.onStart(root.toString(), build)
        manager.onEnd(root.toString(), sync)
        assertTrue(service.gradleActivity.busy.value)
        manager.onEnd(root.toString(), build)
        waitBusy(service, false)
    }

    fun `test 別のプロジェクトのタスクは数えない`() {
        val service = installProjectService()
        val other = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, "another project")
        manager.onStart(root.toString(), other)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertFalse(service.gradleActivity.busy.value)
        manager.onEnd(root.toString(), other)
    }
}
