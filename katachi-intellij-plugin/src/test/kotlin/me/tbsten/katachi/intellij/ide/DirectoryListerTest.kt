package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable

/** [IdeDirectoryLister] over the real disk, VFS and `ProjectFileIndex` (design ide-review-screens 3.2). */
internal class DirectoryListerTest : KatachiIdeTestBase() {
    private var contentRoot: VirtualFile? = null

    override fun tearDown() {
        try {
            contentRoot?.takeIf { it.isValid }?.let { PsiTestUtil.removeContentEntry(module, it) }
            contentRoot = null
        } finally {
            super.tearDown()
        }
    }

    private fun vfsOf(path: Path): VirtualFile =
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw AssertionError("Not in the VFS: $path")

    /** Called from a pooled thread, where the plugin calls it (the EDT is not for reading the disk). The outcome, so a thrown exception is not logged as a platform error. */
    private fun listingResult(directory: Path): Result<List<String>> {
        val future = ApplicationManager.getApplication().executeOnPooledThread(
            Callable { runCatching { IdeDirectoryLister(project).childDirectories(directory) } },
        )
        // Not future.get(): the test thread holds the write-intent lock, which the read action waits out.
        PlatformTestUtil.waitWithEventsDispatching({ "listing $directory" }, { future.isDone }, 20)
        return future.get()
    }

    private fun listing(directory: Path): List<String> = listingResult(directory).getOrThrow()

    fun `test 直下のディレクトリの名前だけを返し ファイルは返さない`() {
        val feature = Files.createDirectories(root.resolve("feature"))
        Files.createDirectories(feature.resolve("home"))
        Files.createDirectories(feature.resolve("settings"))
        Files.writeString(feature.resolve("README.md"), "x")

        assertEquals(listOf("home", "settings"), listing(feature).sorted())
    }

    fun `test IDE が除外したディレクトリは出ない`() {
        val project = Files.createDirectories(root.resolve("proj"))
        Files.createDirectories(project.resolve("app"))
        Files.createDirectories(project.resolve("build"))
        contentRoot = vfsOf(project).also { PsiTestUtil.addContentRoot(module, it) }
        PsiTestUtil.addExcludedRoot(module, vfsOf(project.resolve("build")))

        assertEquals(listOf("app"), listing(project))
    }

    fun `test VFS がまだ知らない新しいディレクトリは除外されていないものとして出る`() {
        val project = Files.createDirectories(root.resolve("fresh"))
        vfsOf(project)
        Files.createDirectories(project.resolve("brand-new"))

        assertEquals(listOf("brand-new"), listing(project))
    }

    fun `test 存在しないディレクトリと ファイルを指されたときは空を返す`() {
        val file = root.resolve("a-file")
        Files.writeString(file, "x")

        assertEquals(emptyList<String>(), listing(root.resolve("missing")))
        assertEquals(emptyList<String>(), listing(file))
    }

    fun `test 読めないディレクトリでは IOException を投げる`() {
        val locked = Files.createDirectories(root.resolve("locked"))
        Files.createDirectories(locked.resolve("inner"))
        val unreadable = locked.toFile().setReadable(false)
        try {
            if (!unreadable || Files.isReadable(locked)) return // running as a user the permission does not bind (root, Windows)
            val failure = listingResult(locked).exceptionOrNull()
            // It must be the IOException itself (not java.nio's DirectoryIteratorException), which is what CaptureCandidates catches.
            assertTrue("IOException expected, got $failure", failure is IOException)
        } finally {
            locked.toFile().setReadable(true)
        }
    }
}
