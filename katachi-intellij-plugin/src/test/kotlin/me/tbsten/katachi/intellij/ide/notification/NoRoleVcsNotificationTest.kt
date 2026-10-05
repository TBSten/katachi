package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.ide.rules.TrackingSome
import me.tbsten.katachi.intellij.ide.rules.settledVcsTrackingOf
import me.tbsten.katachi.intellij.ide.rules.vcsTrackingOf
import me.tbsten.katachi.intellij.ide.rules.withUntrackingVcs
import me.tbsten.katachi.intellij.ide.rules.withVcs
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import me.tbsten.katachi.intellij.testing.STRAY
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The wording about git, driven by the platform's real change list manager over a VCS that tracks what the test says: a file
 * created under a VCS root says "once you add it to git" before anything is added, even in the moment the manager has not scanned
 * it yet, and the notification changes by itself when the VCS stops holding it or when the scan says the file is tracked.
 */
internal class NoRoleVcsNotificationTest : NoRoleTestBase() {
    /** What the real VCS answered the notification, in order: a first draw and the follow-up both ask. */
    private val answered = CopyOnWriteArrayList<VcsTracking>()

    private fun useRealVcs() {
        vcsOf = { file -> vcsTrackingOf(project, file).also { answered += it } }
        vcsSettledOf = { file -> settledVcsTrackingOf(project, file) }
    }

    fun `test 作ったばかりの未追跡ファイルは git の文言で出て VCS が外れると自分で普通の文言に戻る`() {
        fake.emit(ready())
        val file = write(STRAY, "")
        answer(STRAY)
        useRealVcs()

        withUntrackingVcs(project, root) { endMapping ->
            val editor = open(file)
            awaitNoRoleText("untracked", editor, expectedText(STRAY, untracked = true))

            endMapping()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            awaitNoRoleText("no VCS", editor, expectedText(STRAY))
        }
    }

    fun `test VCS が落ち着いた後に作ってすぐ開いたファイルは途中でも追跡済みの文言を出さず未追跡の文言で出る`() {
        fake.emit(ready())
        answer(STRAY)
        useRealVcs()

        withUntrackingVcs(project, root) {
            // Created after the manager settled and opened at once, the manager held back: it has not scanned the file, so it
            // would say "tracked" (it knows no unversioned file yet).
            val manager = ChangeListManagerImpl.getInstanceImpl(project)
            manager.freeze("a test holds back the scan")
            val editor = open(write(STRAY, ""))
            try {
                awaitNoRoleText("untracked from the first draw", editor, expectedText(STRAY, untracked = true))
            } finally {
                manager.unfreeze()
            }
            manager.waitEverythingDoneInTestMode()
            pump(500)

            assertEquals(expectedText(STRAY, untracked = true), peekNoRolePanel(editor)?.text)
            assertFalse("the tracked wording was never asked for: $answered", VcsTracking.Tracked in answered)
        }
    }

    fun `test 走査前の追跡済みファイルは未追跡の文言で出ても走査が終わると自分で普通の文言になる`() {
        fake.emit(ready())
        answer(STRAY)
        useRealVcs()

        withVcs(project, root, TrackingSome { true }) {
            val manager = ChangeListManagerImpl.getInstanceImpl(project)
            manager.freeze("a test holds back the scan")
            val editor = open(write(STRAY, ""))
            try {
                awaitNoRoleText("the guess for a file not scanned", editor, expectedText(STRAY, untracked = true))
            } finally {
                manager.unfreeze()
            }

            manager.waitEverythingDoneInTestMode()
            awaitNoRoleText("tracked after the scan", editor, expectedText(STRAY))
        }
    }

    fun `test 追跡済みのファイルを編集して VCS が走査待ちになっても文言は行き来しない`() {
        fake.emit(ready())
        val file = write(STRAY, "")
        answer(STRAY)
        useRealVcs()

        withVcs(project, root, TrackingSome { true }) {
            val editor = open(file)
            awaitNoRoleText("tracked", editor, expectedText(STRAY))

            // An edit makes the file dirty for a moment: the manager tells the status changed before it has scanned.
            VcsDirtyScopeManager.getInstance(project).fileDirty(file)
            FileStatusManager.getInstance(project).fileStatusesChanged()
            repeat(20) {
                pump(25)
                assertEquals("the wording flipped while the scan was pending", expectedText(STRAY), peekNoRolePanel(editor)?.text)
            }
            ChangeListManagerImpl.getInstanceImpl(project).waitEverythingDoneInTestMode()
            FileStatusManager.getInstance(project).fileStatusesChanged()
            pump(500)
            assertEquals(expectedText(STRAY), peekNoRolePanel(editor)?.text)
        }
    }
}
