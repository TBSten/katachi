package me.tbsten.katachi.intellij.ide.debug

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Anchor
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.newmenu.KatachiNewGroup
import me.tbsten.katachi.intellij.testing.indexOf
import me.tbsten.katachi.intellij.testing.snapshotOf
import me.tbsten.katachi.intellij.ide.newmenu.NewMenuTestBase
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest

/**
 * The doors of `KatachiDebugBridge` that stand in for the Project view's right-click on a local macOS, where Driver's real mouse
 * does not reach the IDE under test: `describeNewMenu` and `chooseNewMenuItemLater`.
 */
internal class DebugBridgeNewMenuTest : NewMenuTestBase() {
    private val example get() = "feature/src/main/com/example"

    private fun <T> withBridge(block: (KatachiDebugBridge) -> T): T {
        val previous = System.getProperty("katachi.debugBridge")
        System.setProperty("katachi.debugBridge", "true")
        try {
            return block(KatachiDebugBridge(project))
        } finally {
            if (previous == null) System.clearProperty("katachi.debugBridge") else System.setProperty("katachi.debugBridge", previous)
        }
    }

    fun `test describeNewMenuはディレクトリに当たるテンプレートを katachi 始まりのパスで1行ずつ返す`() {
        installReadyMenu(screen, viewModel, repository)
        val path = pathOf(directory(example)).toString()

        val menu = withBridge { it.describeNewMenu(path) }

        assertEquals(
            listOf(
                "item=katachi/feature/Screen — feature/<feature>/ui/<screen>Screen.kt|enabled",
                "item=katachi/feature/ViewModel — feature/<feature>/<screen>ViewModel.kt|enabled",
            ),
            menu.lines(),
        )
    }

    fun `test describeNewMenuは当たらないディレクトリと存在しないパスで item=none を返す`() {
        installReadyMenu(screen)
        val unrelated = pathOf(directory("docs/other")).toString()

        withBridge { bridge ->
            assertEquals("item=none", bridge.describeNewMenu(unrelated))
            assertEquals("item=none", bridge.describeNewMenu("/no/such/directory"))
        }
    }

    fun `test chooseNewMenuItemLaterは選んだ項目をEDTに積み、ダイアログをそのディレクトリ起点で開く`() {
        installReadyMenu(screen, viewModel)
        val directory = directory("$example/feature/home")
        val label = "Screen — ui/<screen>Screen.kt"

        val answer = withBridge { it.chooseNewMenuItemLater(pathOf(directory).toString(), "katachi/feature/$label") }
        // Later: the call has returned and nothing has run yet; the choice waits on the EDT (a modal dialog would hold the caller).
        assertTrue(entryEffects.dialogs.isEmpty())
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals("chosen=$label", answer)
        assertEquals(
            listOf(GenerateDialogRequest(EntryOrigin.NewMenuDirectory(pathOf(directory)), TemplateId(ModuleId(root, ":arch-a"), "feature.Screen"), mapOf("feature" to "home"))),
            entryEffects.dialogs.toList(),
        )
    }

    fun `test chooseNewMenuItemLaterは無い項目では何も開かず chosen=none を返す`() {
        installReadyMenu(screen)
        val path = pathOf(directory(example)).toString()

        val answer = withBridge { it.chooseNewMenuItemLater(path, "katachi/feature/No such item") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals("chosen=none", answer)
        assertTrue(entryEffects.dialogs.isEmpty())
    }

    fun `test 読み込み前は押せない Loading の1行だけを返し、それを選んでも何も開かない`() {
        installMenuService { indexOf(snapshotOf(moduleA, listOf(screen))) }
        val path = pathOf(directory(example)).toString()
        val loading = KatachiBundle.message("newMenu.loading")

        withBridge { bridge ->
            assertEquals("item=katachi/$loading|disabled", bridge.describeNewMenu(path))
            assertEquals("chosen=none", bridge.chooseNewMenuItemLater(path, "katachi/$loading"))
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertTrue(entryEffects.dialogs.isEmpty())
    }

    fun `test NewGroup の下に katachi が無ければ item=none と chosen=none を返す`() {
        installReadyMenu(screen)
        val path = pathOf(directory(example)).toString()
        val platformNew = ActionManager.getInstance().getAction(IdeActions.GROUP_NEW) as DefaultActionGroup
        val katachi = ActionManager.getInstance().getAction(KatachiNewGroup.ID)
        platformNew.remove(katachi)
        try {
            withBridge { bridge ->
                assertEquals("item=none", bridge.describeNewMenu(path))
                assertEquals("chosen=none", bridge.chooseNewMenuItemLater(path, "katachi/feature/Screen — feature/<feature>/ui/<screen>Screen.kt"))
            }
        } finally {
            platformNew.add(katachi, Constraints(Anchor.AFTER, "NewDir"))
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertTrue(entryEffects.dialogs.isEmpty())
    }

    fun `test バイパスが切れているとchooseNewMenuItemLaterも動かない`() {
        installReadyMenu(screen)
        val path = pathOf(directory(example)).toString()

        assertThrows(IllegalStateException::class.java) { KatachiDebugBridge(project).chooseNewMenuItemLater(path, "katachi/feature/x") }
    }

    fun `test バイパスが切れているとNewメニューの口は動かない`() {
        installReadyMenu(screen)
        val path = pathOf(directory(example)).toString()

        assertThrows(IllegalStateException::class.java) { KatachiDebugBridge(project).describeNewMenu(path) }
    }
}
