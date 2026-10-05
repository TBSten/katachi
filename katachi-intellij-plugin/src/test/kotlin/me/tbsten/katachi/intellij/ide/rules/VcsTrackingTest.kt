package me.tbsten.katachi.intellij.ide.rules

import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.ide.KatachiIdeTestBase
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import java.nio.file.Files

/**
 * What the version control says of a file (design 4.4, U-6): no VCS, a VCS that does not track the file yet (the state of a file
 * just created, before `git add`), and the combinations of the three answers. The tracked / ignored cases of a real git
 * are out of reach here (TODO: V3, the Driver smoke over a `git init` copy).
 */
internal class VcsTrackingTest : KatachiIdeTestBase() {
    private fun newFile(relative: String): VirtualFile {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, "")
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw AssertionError("not in the VFS: $path")
    }

    fun `test VCS の答えの組はどれも1つの状態になり VCS が無いが最優先で次に無視 次に未追跡 走査待ちは未追跡寄り`() {
        val expected = mapOf(
            VcsReading(hasVcs = false, ignored = false, unversioned = false, scanPending = false) to VcsTracking.NoVcs,
            VcsReading(hasVcs = false, ignored = true, unversioned = true, scanPending = true) to VcsTracking.NoVcs,
            VcsReading(hasVcs = true, ignored = true, unversioned = true, scanPending = false) to VcsTracking.Ignored,
            VcsReading(hasVcs = true, ignored = true, unversioned = false, scanPending = true) to VcsTracking.Ignored,
            VcsReading(hasVcs = true, ignored = false, unversioned = true, scanPending = false) to VcsTracking.Untracked,
            VcsReading(hasVcs = true, ignored = false, unversioned = false, scanPending = false) to VcsTracking.Tracked,
            VcsReading(hasVcs = true, ignored = false, unversioned = false, scanPending = true) to VcsTracking.Untracked,
        )
        for ((reading, tracking) in expected) {
            assertEquals(reading.toString(), tracking, reading.tracking)
        }
    }

    fun `test 答えが走査待ちだけで決まる時だけ未確定でそれ以外は確定している`() {
        val unsettled = VcsReading(hasVcs = true, ignored = false, unversioned = false, scanPending = true)
        assertFalse(unsettled.settled)
        assertTrue(unsettled.copy(scanPending = false).settled)
        assertTrue(unsettled.copy(unversioned = true).settled)
        assertTrue(unsettled.copy(ignored = true).settled)
        assertTrue(unsettled.copy(hasVcs = false).settled)
    }

    fun `test VCS の無いディレクトリのファイルは NoVcs`() {
        assertEquals(VcsTracking.NoVcs, vcsTrackingOf(project, newFile("src/A.kt")))
    }

    fun `test VCS に属して追跡されていない作ったばかりのファイルは Untracked でマッピングを外すと NoVcs に戻る`() {
        val file = newFile("src/Paging.kt")
        withUntrackingVcs(project, root) { endMapping ->
            VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
            ChangeListManagerImpl.getInstanceImpl(project).waitEverythingDoneInTestMode()

            assertEquals(VcsTracking.Untracked, vcsTrackingOf(project, file))

            endMapping()
            assertEquals(VcsTracking.NoVcs, vcsTrackingOf(project, file))
        }
    }

    fun `test VCS が落ち着いた後に作ったファイルは走査される前から追跡済みとは答えず走査のあとに未追跡で確定する`() {
        withUntrackingVcs(project, root) {
            val file = newFile("src/Fresh.kt")

            assertEquals("not scanned yet: a new file is not 'tracked'", VcsTracking.Untracked, vcsTrackingOf(project, file))
            ChangeListManagerImpl.getInstanceImpl(project).waitEverythingDoneInTestMode()
            assertEquals(VcsTracking.Untracked, vcsTrackingOf(project, file))
            assertEquals(VcsTracking.Untracked, settledVcsTrackingOf(project, file))
        }
    }

    fun `test 追跡されているファイルは走査前は未確定で走査のあとに追跡済みで確定する`() {
        withVcs(project, root, TrackingSome { true }) {
            val file = newFile("src/Old.kt")

            assertNull("the manager has not scanned the file", settledVcsTrackingOf(project, file))
            assertEquals("the first draw guesses a new file", VcsTracking.Untracked, vcsTrackingOf(project, file))
            ChangeListManagerImpl.getInstanceImpl(project).waitEverythingDoneInTestMode()
            assertEquals(VcsTracking.Tracked, settledVcsTrackingOf(project, file))
            assertEquals(VcsTracking.Tracked, vcsTrackingOf(project, file))
        }
    }
}
