package me.tbsten.katachi.intellij.ide.rules

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import me.tbsten.katachi.intellij.ide.definition.DefinitionServiceTestBase
import me.tbsten.katachi.intellij.ide.definition.KatachiDefinitionService
import me.tbsten.katachi.intellij.presentation.rules.roleSuggestionDraftFor
import java.awt.datatransfer.DataFlavor

/** `katachi.CopyRoleSuggestion` over the real definition service: the draft is relative to the definition's project root. */
internal class CopyRoleSuggestionActionTest : DefinitionServiceTestBase() {
    private fun eventFor(action: CopyRoleSuggestionAction, relative: String) = TestActionEvent.createTestEvent(
        action,
        SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, writeFile(relative, ""))
            .build(),
    )

    fun `test 選んだファイルの下書きを定義のルートからの相対パスでクリップボードに入れる`() {
        install()
        project.replaceService(KatachiDefinitionService::class.java, definition, testRootDisposable)
        loaded()
        val action = ActionManager.getInstance().getAction(CopyRoleSuggestionAction.ID) as CopyRoleSuggestionAction
        val event = eventFor(action, "src/other/Stray.kt")

        action.update(event)
        assertTrue("shown in a project with katachi", event.presentation.isEnabledAndVisible)
        action.actionPerformed(event)

        val expected = roleSuggestionDraftFor("src/other/Stray.kt")
        PlatformTestUtil.waitWithEventsDispatching(
            { "clipboard: ${CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)}" },
            { CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor) == expected },
            10,
        )
    }

    fun `test 選んだものがフォルダなら出ない`() {
        install()
        val action = CopyRoleSuggestionAction()
        val directory = writeFile("src/other/Stray.kt", "").parent
        val event = TestActionEvent.createTestEvent(
            action,
            SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.VIRTUAL_FILE, directory).build(),
        )

        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }
}
