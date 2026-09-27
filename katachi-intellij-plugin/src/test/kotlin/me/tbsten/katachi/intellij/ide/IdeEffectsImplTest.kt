package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.async
import me.tbsten.katachi.intellij.presentation.DocsPage
import java.nio.file.Files

/** The IDE effects one by one, on real files under a temporary directory. */
internal class IdeEffectsImplTest : KatachiIdeTestBase() {
    private val effects get() = IdeEffectsImpl(project)

    /** Runs a suspending effect off the EDT and pumps events until it completes. */
    private fun <T> run(block: suspend () -> T): T {
        val deferred = scope.async { block() }
        PlatformTestUtil.waitWithEventsDispatching("effect", { deferred.isCompleted }, 10)
        @Suppress("OPT_IN_USAGE")
        return deferred.getCompleted()
    }

    fun `test 新しいディレクトリに書かれたファイルも VFS に反映する`() {
        val file = root.resolve("feature/src/main/kotlin/New.kt")
        Files.createDirectories(file.parent)
        Files.writeString(file, "class New\n")

        run { effects.refreshFiles(listOf(file)) }

        assertNotNull(LocalFileSystem.getInstance().findFileByNioFile(file))
    }

    fun `test 開いているファイルを上書きしたら反映でエディタの中身も読み直される`() {
        val file = root.resolve("Existing.kt")
        Files.writeString(file, "// v1\n")
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?: throw AssertionError("not in VFS")
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
        val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: throw AssertionError("no document")
        assertEquals("// v1\n", document.text)

        Files.writeString(file, "// v2 written by katachi\n")
        run { effects.refreshFiles(listOf(file)) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals("// v2 written by katachi\n", document.text)
    }

    fun `test 生成したファイルを開き、最初のファイルにフォーカスする`() {
        val first = root.resolve("A.kt").also { Files.writeString(it, "class A\n") }
        val second = root.resolve("B.kt").also { Files.writeString(it, "class B\n") }

        run { effects.openFiles(listOf(first, second)) }

        // The test editor manager does not keep tab order, so only which files and which one is selected.
        assertEquals(setOf(first, second), openFiles().toSet())
        assertEquals(first, FileEditorManager.getInstance(project).selectedFiles.single().toNioPath())
    }

    fun `test Local History のラベル名にテンプレート名を並べる`() {
        val name = run { effects.putLocalHistoryLabel(listOf("Repository", "UseCase")) }

        assertEquals(KatachiBundle.message("localHistory.label", "Repository, UseCase"), name)
    }

    fun `test ドキュメントの案内は日本語のページを指す`() {
        assertEquals("https://tbsten.github.io/katachi/ja/guides/generate-code-from-template/", docsUrlOf(DocsPage.WritingTemplates))
    }
}
