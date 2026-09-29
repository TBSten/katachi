package me.tbsten.katachi.intellij.ide

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.ide.dialog.realGenerateDialogEnvironmentOf
import me.tbsten.katachi.intellij.ide.notification.link
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry

/**
 * A retry from an entry after a failed generation, through the production path the notification's
 * [Create] takes (V2 H1): the generation the entry starts and the check the dialog makes are the same
 * one, so the file is recognised as still holding the provisional content this plugin wrote (issue 16),
 * not merely as empty by the empty-file rule.
 */
internal class EntryRetryTest : EntryToGenerationTestBase() {
    // covers: 論点16
    fun `test 通知から生成して Gradle が失敗した後に開き直したダイアログは自分の仮の中身と判定する`() {
        val editor = open(writeFile(target, ""))
        gradle.plans += PlannedGeneration(fails = true)
        answerWith()

        waitForPanel(editor).link("notification.editor.create").doClick()

        assertTrue(awaitLedger(target) is LedgerEntry.Failed)
        val environment = realGenerateDialogEnvironmentOf(project, dialogs.single())
        val check = service.scope.async(Dispatchers.Default) { environment.checkTarget(target) }.awaitPumping()
        assertEquals(TargetState.OwnProvisional, check)
        assertEquals(1, balloonTexts().size)
    }
}
