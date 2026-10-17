package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiConfigurable
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry

/** The real provider through a sequence of user actions (issues 13, 15, the settings page). */
internal class EditorNotificationSequenceTest : EditorNotificationTestBase() {

    // covers: 論点15
    fun `test 開く と × と開き直し と別のファイル とサービスの作り直しの順で記憶のとおりに出る`() {
        loadIndex()
        val a = write("feature/a/ui/Home.kt", "")
        val b = write("feature/b/ui/Home.kt", "")

        val shownA = panelOf(open(a))
        assertNotNull("A opened", shownA)
        shownA!!.button("notification.editor.dismiss.tooltip").doClick()
        assertNull("A after x", panelOf(editorOf(a)))
        assertNull("A reopened", panelOf(reopen(a)))
        assertNotNull("B opened", panelOf(open(b)))

        recreateMemoryService()

        assertNotNull("A in a new session", panelOf(reopen(a)))
    }

    // covers: 論点14
    fun `test 開いているファイルで設定の全体を OFF にして適用するとパネルが消える`() {
        loadIndex()
        val editor = open("feature/a/ui/Home.kt", "")
        assertNotNull(panelOf(editor))
        val configurable = KatachiConfigurable(project)
        try {
            val component = configurable.createComponent()
            configurable.reset()
            val text = KatachiBundle.message("settings.editorNotification.enabled")
            UIUtil.findComponentsOfType(component, JBCheckBox::class.java).single { it.text == text }.doClick()

            configurable.apply()

            assertNull(panelOf(editor))
        } finally {
            configurable.disposeUIResources()
        }
    }

    // covers: 論点13
    fun `test 開いている空のファイルに中身を打つと保存しなくても中身ありの通知に変わる`() {
        loadIndex()
        val file = write("feature/a/ui/Home.kt", "package com.example\n")
        val editor = open(file)
        assertEquals(noticeTextOf("empty", "feature/a/ui/Home.kt"), panelOf(editor)?.text)
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        WriteCommandAction.runWriteCommandAction(project) { document.setText("package com.example\n\nclass Home\n") }

        val matched = noticeTextOf("matched", "feature/a/ui/Home.kt")
        PlatformTestUtil.waitWithEventsDispatching({ "panel: ${panelOf(editor)?.text}" }, { panelOf(editor)?.text == matched }, 10)
    }

    // covers: 論点16
    fun `test 台帳が成功直後になると開いているファイルの通知が消え台帳が消えても戻らない`() {
        loadIndex()
        val file = write("feature/a/ui/Home.kt", "")
        val editor = open(file)
        val template = matchesOf("feature/a/ui/Home.kt").first().id
        assertNotNull(panelOf(editor))

        service.ledger.markSucceeded(path("feature/a/ui/Home.kt"), template)
        PlatformTestUtil.waitWithEventsDispatching({ "panel: ${panelOf(editor)?.text}" }, { panelOf(editor) == null }, 10)
        service.ledger.clear(path("feature/a/ui/Home.kt"))
        assertNull("the ledger forgot the file", service.ledger.entryOf(path("feature/a/ui/Home.kt")))
        updateAll()

        assertNull(panelOf(editor))
    }

    // covers: E-3
    fun `test 生成して消したファイルを空のまま作り直すと通知がまた出る`() {
        loadIndex()
        val file = write("feature/a/ui/Home.kt", "")
        val editor = open(file)
        val template = matchesOf("feature/a/ui/Home.kt").first().id
        service.ledger.markSucceeded(path("feature/a/ui/Home.kt"), template)
        PlatformTestUtil.waitWithEventsDispatching({ "panel: ${panelOf(editor)?.text}" }, { panelOf(editor) == null }, 10)
        close(file)

        WriteAction.runAndWait<Throwable> { file.delete(this) }
        PlatformTestUtil.waitWithEventsDispatching({ "ledger: ${service.ledger.entries.value}" }, { service.ledger.entryOf(path("feature/a/ui/Home.kt")) == null }, 10)
        val again = open("feature/a/ui/Home.kt", "")

        assertNotNull("the file is new again", panelOf(again))
    }

    // covers: E-3
    fun `test 生成したファイルが入ったディレクトリごと消してもほかのファイルの台帳は残る`() {
        loadIndex()
        val gone = write("feature/a/ui/Home.kt", "")
        val kept = write("feature/b/ui/Home.kt", "")
        val template = matchesOf("feature/a/ui/Home.kt").first().id
        service.ledger.markSucceeded(path("feature/a/ui/Home.kt"), template)
        service.ledger.markSucceeded(path("feature/b/ui/Home.kt"), template)
        open(gone)
        settleNotifications() // the platform decides in the background; the refresher follows the ledger from the first decision on

        WriteAction.runAndWait<Throwable> { gone.parent.parent.delete(this) }

        PlatformTestUtil.waitWithEventsDispatching({ "ledger: ${service.ledger.entries.value}" }, { service.ledger.entryOf(path("feature/a/ui/Home.kt")) == null }, 10)
        assertTrue("the other directory stays", kept.isValid)
        assertNotNull("its entry stays", service.ledger.entryOf(path("feature/b/ui/Home.kt")))
    }

    // covers: E-3
    fun `test 失敗や実行中の台帳はファイルを消しても残る`() {
        loadIndex()
        val file = write("feature/a/ui/Home.kt", "")
        open(file)
        settleNotifications()
        val template = matchesOf("feature/a/ui/Home.kt").first().id
        service.ledger.markFailed(path("feature/a/ui/Home.kt"), template, "katachi generate")

        WriteAction.runAndWait<Throwable> { file.delete(this) }
        settleNotifications()

        assertTrue("that generation is not over", service.ledger.entryOf(path("feature/a/ui/Home.kt")) is LedgerEntry.Failed)
    }
}
