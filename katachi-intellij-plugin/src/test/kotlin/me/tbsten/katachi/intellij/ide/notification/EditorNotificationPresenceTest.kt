package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import me.tbsten.katachi.intellij.ide.DefinitionModulePresence
import me.tbsten.katachi.intellij.ide.LoadAnswer

/**
 * The notification asks [DefinitionModulePresence] whether the project has katachi (decision 17), and a Gradle sync that ends
 * with a definition module makes the notification come without an edit.
 */
internal class EditorNotificationPresenceTest : EditorNotificationTestBase() {
    @Volatile private var hasModule = false

    /** The test base's ports with the answer of the presence service, as [NotificationPorts.of] gives it. */
    override fun ports(): NotificationPorts = super.ports().let { base ->
        NotificationPorts(
            existingService = base.existingService,
            hasDefinitionModule = { DefinitionModulePresence.getInstance(project).hasDefinitionModule() },
            service = base.service,
            settings = base.settings,
            memory = base.memory,
        )
    }

    // covers: 論点17
    fun `test モジュールが無かった間に開いた空のファイルは 同期が終わって答えが変わると編集なしで通知の対象になる`() {
        project.replaceService(DefinitionModulePresence::class.java, DefinitionModulePresence(project) { hasModule }, testRootDisposable)
        gradle.loads += LoadAnswer(NOTIFICATION_JSON)

        val editor = open("feature/a/ui/Home.kt", "")
        assertNull(panelOf(editor))
        assertEquals("a project without a definition module creates nothing", 0, servicesCreated.get())

        hasModule = true
        project.messageBus.syncPublisher(ProjectDataImportListener.TOPIC).onImportFinished("/w/repo")

        PlatformTestUtil.waitWithEventsDispatching({ "services created: ${servicesCreated.get()}" }, { servicesCreated.get() == 1 }, 10)
    }
}
