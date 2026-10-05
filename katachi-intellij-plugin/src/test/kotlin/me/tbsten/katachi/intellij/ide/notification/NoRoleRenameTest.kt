package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile

/**
 * The notification follows the file's name and place, over real files in the real VFS and the real definition service: the
 * role that holds a file depends on its path, and the notification itself suggests "move it" and "rename it". The platform
 * does not listen to the VFS for this, so these are the operations (rename, move, rename the directory, and back) that
 * must redraw by themselves.
 */
internal class NoRoleRenameTest : NoRoleRealServiceTestBase() {
    private fun rename(file: VirtualFile, name: String) {
        WriteAction.runAndWait<Throwable> { file.rename(this, name) }
    }

    private fun move(file: VirtualFile, directory: VirtualFile) {
        WriteAction.runAndWait<Throwable> { file.move(this, directory) }
    }

    fun `test 名前を変えて当たるようになると通知が消え外れる名前に戻すと出る`() {
        installReal()
        val file = write("src/service/Notes.txt", "")
        val editor = open(file)
        awaitPanel("a name no layout entry takes", editor, present = true)
        pump(600)

        rename(file, "Notes.md")
        awaitPanel("renamed to a name the role holds", editor, present = false)

        rename(file, "Notes.txt")
        awaitPanel("renamed back", editor, present = true)
    }

    fun `test 別のディレクトリへ移して当たるようになると通知が消え戻すと出る`() {
        installReal()
        val held = write("src/service/HealthService.kt", "")
        val file = write("src/other/Stray.kt", "")
        val editor = open(file)
        awaitPanel("outside the role's directory", editor, present = true)
        pump(600)

        move(file, held.parent)
        awaitPanel("moved into the role's directory", editor, present = false)

        move(file, write("src/other/Keep.kt", "").parent)
        awaitPanel("moved back", editor, present = true)
    }

    fun `test ディレクトリの名前を変えるとその下の開いているファイルの通知も描き直される`() {
        installReal()
        val file = write("src/servicex/Stray.kt", "")
        val editor = open(file)
        awaitPanel("under a directory no role holds", editor, present = true)
        pump(600)

        rename(file.parent, "service")
        awaitPanel("the directory became the role's", editor, present = false)

        rename(file.parent, "servicex")
        awaitPanel("the directory was renamed back", editor, present = true)
    }

    fun `test 開いていないファイルの名前を変えても何も起きず開いたときの判定が新しい名前で行われる`() {
        installReal()
        val file = write("src/service/Notes.txt", "")
        rename(file, "Notes.md")

        val editor = open(file)
        pump(800)

        assertNull("held under its new name", noRolePanelOf(editor))
    }
}
