package me.tbsten.katachi.intellij.ide.newmenu

import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.notification.NotificationPorts
import me.tbsten.katachi.intellij.ide.notification.entrySettingsOf
import me.tbsten.katachi.intellij.testing.indexOf
import me.tbsten.katachi.intellij.testing.snapshotOf

/**
 * When New › katachi appears at all (issue 3, decisions 16, 17): a project without katachi never gets
 * the project service or a "Loading templates..." item (V2 M1), and turning "load without a user
 * action" back on brings the menu back without opening the tool window (V2 M3).
 */
internal class NewMenuAvailabilityTest : NewMenuTestBase() {
    private val example get() = "feature/src/main/com/example"

    // covers: 論点3
    fun `test katachi の定義モジュールが無いプロジェクトでは最初に New を開いても何も出さずサービスも作らない`() {
        var created = 0
        val group = KatachiNewGroup { ports(existing = null, hasDefinitionModule = false) { created++ } }
        val event = eventOn(directory(example), action = group)

        group.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
        assertEquals(0, group.getChildren(event).size)
        assertEquals("the project service must not be created without katachi", 0, created)
    }

    // covers: 論点3
    fun `test 定義モジュールがあればまだ無いサービスを作って読み込み中の項目を出す`() {
        installMenuService { indexOf(snapshotOf(moduleA, listOf(screen))) }
        val installed = service
        var created = 0
        val group = KatachiNewGroup { ports(existing = null, hasDefinitionModule = true) { created++; installed } }
        val event = eventOn(directory(example), action = group)

        group.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
        assertEquals(1, created)
    }

    // covers: 論点11
    fun `test 操作なしの読み込みを OFF から ON に戻すと次に New を開いたときに読み込みを始めテンプレートが出る`() {
        setAutoLoad(false)
        installReadyMenu(screen)
        waitForIndex("disabled") { it is PlacementIndexState.Unavailable }
        assertEquals(emptyList<String>(), menuOn(directory(example)))
        assertTrue(gradle.loadRequests.isEmpty())

        setAutoLoad(true)
        menuOn(directory(example))
        settle()

        waitForIndex("ready") { it is PlacementIndexState.Ready }
        assertEquals(1, gradle.loadRequests.size)
        assertEquals(listOf("feature", "  Screen — feature/<feature>/ui/<screen>Screen.kt"), menuOn(directory(example)))
    }

    private fun ports(existing: KatachiProjectService?, hasDefinitionModule: Boolean, create: () -> Any?): NotificationPorts = NotificationPorts(
        existingService = { existing },
        hasDefinitionModule = { hasDefinitionModule },
        service = { create() as? KatachiProjectService ?: throw AssertionError("no project service for this test") },
        settings = { entrySettingsOf(KatachiSettings.getInstance(project)) },
        memory = { throw AssertionError("New › katachi does not use the notification memory") },
    )
}
