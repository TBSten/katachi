package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.EditorNotificationProvider
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.ide.newmenu.KatachiNewGroup
import me.tbsten.katachi.intellij.ide.notification.KatachiEditorNotificationProvider

/** plugin.xml registers the two entries: the editor notification and New › katachi. */
internal class EntryRegistrationTest : AnalysisTestBase() {

    // covers: 論点17
    fun `test エディタの通知の Provider が1つだけ登録されている`() {
        val registered = EditorNotificationProvider.EP_NAME.getExtensions(project).filterIsInstance<KatachiEditorNotificationProvider>()

        assertEquals("katachi's providers: $registered", 1, registered.size)
    }

    // covers: 論点12
    fun `test New の直下に katachi のサブメニューが Directory の後ろに登録されている`() {
        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction(KatachiNewGroup.ID)
        assertTrue("registered as $group", group is KatachiNewGroup)
        val presentation = group.templatePresentation
        assertTrue("a submenu, not items inlined into New", presentation.isPopupGroup)
        assertEquals("katachi", presentation.text)

        val newGroup = actionManager.getAction("NewGroup") as DefaultActionGroup
        // The group may hold the stub rather than the loaded instance, so compare the ids.
        val childIds = newGroup.getChildActionsOrStubs().map(actionManager::getId)
        val index = childIds.indexOf(KatachiNewGroup.ID)
        assertTrue("New's children: $childIds", index > 0)
        assertEquals("right after Directory / Package", "NewDir", childIds[index - 1])
    }
}
