package me.tbsten.katachi.intellij.ide.notification

import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.HELD
import me.tbsten.katachi.intellij.testing.STRAY
import me.tbsten.katachi.intellij.testing.rolesFor

/**
 * `KatachiDebugBridge.describeNoRole`, the String door the Driver smoke reads the notification through. It must say what
 * is on the screen: the same words as the panel, in the order of its links, and nothing when no panel is up.
 */
internal class DebugBridgeNoRoleTest : NoRoleTestBase() {
    private fun <T> withBridge(block: (KatachiDebugBridge) -> T): T {
        val previous = System.getProperty("katachi.debugBridge")
        System.setProperty("katachi.debugBridge", "true")
        try {
            return block(KatachiDebugBridge(project))
        } finally {
            if (previous == null) System.clearProperty("katachi.debugBridge") else System.setProperty("katachi.debugBridge", previous)
        }
    }

    private fun describe(): List<String> = withBridge { it.describeNoRole() }.lines()

    fun `test 出ている通知の文言と近い Role とリンクの並びを返す`() {
        fake.emit(ready())
        declareSites()
        val editor = openAnswered(STRAY)
        assertNotNull(noRolePanelOf(editor))

        val lines = describe()

        val nearest = rolesFor(STRAY).nearby.first()
        assertEquals("file=Paging.kt", lines[0])
        assertEquals("noRole=shown", lines[1])
        assertEquals("text=${KatachiBundle.message("notification.rules.noRole.sure")}", lines[2])
        assertTrue(lines.toString(), lines[3].startsWith("nearby=") && nearest.role.qualifiedName in lines[3])
        assertEquals(
            listOf("link=${KatachiBundle.message("notification.rules.nearbyRoles")}", "link=${KatachiBundle.message("notification.rules.openDefinition")}"),
            lines.filter { it.startsWith("link=") },
        )
    }

    fun `test 当たるファイルでは通知が無いと返しファイル名は選ばれているエディタのもの`() {
        fake.emit(ready())
        declareSites()
        val editor = openAnswered(HELD)
        assertNull(noRolePanelOf(editor))

        assertEquals(listOf("file=GetUserUseCase.kt", "noRole=none"), describe())
    }

    fun `test エディタが開いていないときは none を返す`() {
        assertEquals(listOf("file=none", "noRole=none"), describe())
    }

    fun `test 未追跡の文言を返す`() {
        vcsOf = { VcsTracking.Untracked }
        fake.emit(ready())
        val editor = openAnswered(STRAY)
        assertNotNull(noRolePanelOf(editor))

        assertEquals("text=${KatachiBundle.message("notification.rules.noRole.untracked.sure")}", describe().single { it.startsWith("text=") })
    }

    fun `test 定義が古いときは文末に古さの注記が付いて返り読み込み直すと消える`() {
        val staleness = Freshness.Stale(DEFINITION_INSTANT, StaleReason.DefinitionChanged)
        fake.emit(ready(freshness = staleness))
        fake.answer(path(STRAY), rolesFor(STRAY, snapshot, staleness))
        val editor = open(STRAY, "")
        assertNotNull(noRolePanelOf(editor))

        val stale = describe().single { it.startsWith("text=") }
        assertTrue(stale, stale.endsWith(KatachiBundle.message("notification.rules.noRole.stale")))

        fake.emit(ready())
        answer(STRAY)
        awaitNoRoleText("fresh again", editor, expectedText(STRAY))
        assertEquals("text=${KatachiBundle.message("notification.rules.noRole.sure")}", describe().single { it.startsWith("text=") })
    }

    fun `test 設定で切った通知は none に変わる`() {
        fake.emit(ready())
        val editor = openAnswered(STRAY)
        assertNotNull(noRolePanelOf(editor))

        KatachiSettings.getInstance(project).notifyOnNoRole = false
        updateAll()

        assertEquals("noRole=none", describe().last())
    }

    fun `test デバッグ用の入口が無効なときは読めない`() {
        assertThrows(IllegalStateException::class.java) { KatachiDebugBridge(project).describeNoRole() }
    }
}
