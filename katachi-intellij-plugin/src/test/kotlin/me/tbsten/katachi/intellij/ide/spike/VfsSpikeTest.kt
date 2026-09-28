package me.tbsten.katachi.intellij.ide.spike

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.fileEditor.impl.MemoryDiskConflictResolver
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.async
import me.tbsten.katachi.intellij.ide.IdeEffectsImpl
import me.tbsten.katachi.intellij.ide.KatachiIdeTestBase
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/** S2 (a)(a2)(a3): the provisional file, then the fake Gradle overwriting it through nio. */
internal class VfsSpikeTest : KatachiIdeTestBase() {
    private val effects get() = IdeEffectsImpl(project)
    private val documents get() = FileDocumentManager.getInstance()

    // A test that leaves a Document unsaved (the "keep memory changes" answer) made the next conflict go
    // unasked (run10); dropping them keeps the tests independent.
    override fun tearDown() {
        try {
            val unsaved = documents.unsavedDocuments
            if (unsaved.isNotEmpty()) spike("tearDown.unsavedDropped", unsaved.size)
            WriteAction.run<Throwable> { (documents as FileDocumentManagerImpl).dropAllUnsavedDocuments() }
        } finally {
            super.tearDown()
        }
    }

    /**
     * MemoryDiskConflictResolver.beforeContentChange only records the conflict and asks through
     * Application.invokeLater (javap of 261), so the question can come after refreshFiles has returned and after
     * one dispatchAllEventsInIdeEventQueue (the full test run once saw none: logs/ide-S2-test-all.log).
     */
    private fun RecordingConflictResolver.awaitAsked(key: String) {
        val start = System.nanoTime()
        try {
            PlatformTestUtil.waitWithEventsDispatching("conflict question", { asked.isNotEmpty() }, 5)
        } catch (e: Throwable) {
            spike("$key.awaitAsked.timeout", "${e.javaClass.name}: ${e.message}")
        }
        spike("$key.awaitAsked.ms", (System.nanoTime() - start) / 1_000_000)
    }

    private fun <T> run(block: suspend () -> T): T {
        val deferred = scope.async { block() }
        PlatformTestUtil.waitWithEventsDispatching("effect", { deferred.isCompleted }, 10)
        @Suppress("OPT_IN_USAGE")
        return deferred.getCompleted()
    }

    /** The provisional file as E3 would write it: missing directories, VfsUtil.saveText, open. */
    private fun writeProvisional(path: Path, text: String): VirtualFile {
        val file = WriteAction.compute<VirtualFile, Throwable> {
            val dir = VfsUtil.createDirectoryIfMissing(path.parent.toString()) ?: throw AssertionError("no dir")
            val child = dir.findChild(path.fileName.toString()) ?: dir.createChildData(this, path.fileName.toString())
            VfsUtil.saveText(child, text)
            child
        }
        FileEditorManager.getInstance(project).openFile(file, true)
        return file
    }

    // covers: 論点16
    fun `test a 仮のファイルを開いたまま nio で上書きし refreshFiles すると Document が新しい中身になる`() {
        val path = root.resolve("feature/src/main/kotlin/com/example/HomeScreen.kt")
        val file = writeProvisional(path, "// katachi: generating...\n")
        val document = documents.getDocument(file) ?: throw AssertionError("no document")
        spike("a.provisional.isFileModified", documents.isFileModified(file))

        Files.writeString(path, "package com.example\n\nclass HomeScreen\n")
        run { effects.refreshFiles(listOf(path)) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        spike("a.after.documentText", document.text)
        assertEquals("package com.example\n\nclass HomeScreen\n", document.text)
        assertFalse(documents.isFileModified(file))
    }

    // covers: 論点16
    fun `test a 同じ長さの中身を直後に nio で上書きしても Document が新しい中身になる`() {
        // The VFS decides "changed" from the timestamp and the length; the same length within the same
        // millisecond is the case it could miss.
        val path = root.resolve("Same.kt")
        val file = writeProvisional(path, "AAAA\n")
        val document = documents.getDocument(file) ?: throw AssertionError("no document")
        val before = Files.getLastModifiedTime(path)

        Files.writeString(path, "BBBB\n")
        spike("a.sameLength.timestampEqual", before == Files.getLastModifiedTime(path))
        run { effects.refreshFiles(listOf(path)) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        spike("a.sameLength.documentText", document.text)
        assertEquals("BBBB\n", document.text)
    }

    // covers: 論点16
    fun `test a 同じ長さで時刻も戻した上書きは refreshFiles でも Document に届かない`() {
        val path = root.resolve("SameStamp.kt")
        val file = writeProvisional(path, "AAAA\n")
        val document = documents.getDocument(file) ?: throw AssertionError("no document")
        val before = Files.getLastModifiedTime(path)

        Files.writeString(path, "BBBB\n")
        Files.setLastModifiedTime(path, before)
        run { effects.refreshFiles(listOf(path)) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        spike("a.sameStamp.documentText", document.text)
        spike("a.sameStamp.vfsText", VfsUtil.loadText(file))
        // Observation: the VFS keeps the old content, so the editor too.
        assertEquals("AAAA\n", document.text)
    }

    // covers: 論点16
    fun `test a2 未保存の変更がある Document の裏で nio 書き込みして refreshFiles するとテストでは衝突の例外になる`() {
        // No resolver installed: the test platform's MemoryDiskConflictResolver throws where the IDE shows
        // the "File Cache Conflict" dialog. It surfaces when the EDT events are dispatched, so it is caught there.
        val path = root.resolve("Dirty.kt")
        val file = writeProvisional(path, "// provisional\n")
        val document = documents.getDocument(file) ?: throw AssertionError("no document")
        WriteCommandAction.runWriteCommandAction(project) { document.setText("// provisional\n// typed by the user\n") }
        val resolver = RecordingConflictResolver(reload = null)
        (documents as FileDocumentManagerImpl).setAskReloadFromDisk(testRootDisposable, resolver)

        Files.writeString(path, "class Generated\n")
        run { effects.refreshFiles(listOf(path)) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        resolver.awaitAsked("a2.default")

        spike("a2.default.thrown", resolver.thrown.map { "${it.javaClass.name}: ${it.message}" })
        assertEquals(listOf(file), resolver.asked)
        assertTrue(resolver.thrown.single() is IllegalStateException)
    }

    // covers: 論点16
    fun `test a2 衝突で利用者がメモリの変更を残すと Document は未保存のまま VFS とディスクは生成物になる`() {
        val path = root.resolve("DirtyKeep.kt")
        val file = writeProvisional(path, "// provisional\n")
        val document = documents.getDocument(file) ?: throw AssertionError("no document")
        WriteCommandAction.runWriteCommandAction(project) { document.setText("// typed by the user\n") }
        val resolver = RecordingConflictResolver(reload = false)
        (documents as FileDocumentManagerImpl).setAskReloadFromDisk(testRootDisposable, resolver)

        Files.writeString(path, "class Generated\n")
        run { effects.refreshFiles(listOf(path)) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        resolver.awaitAsked("a2.keep")

        spike("a2.keep.asked", resolver.asked.map { it.name })
        spike("a2.keep.documentText", document.text.replace("\n", "\\n"))
        spike("a2.keep.isFileModified", documents.isFileModified(file))
        spike("a2.keep.vfsText", VfsUtil.loadText(file).replace("\n", "\\n"))
        assertEquals(listOf(file), resolver.asked)
        assertEquals("// typed by the user\n", document.text)
        assertTrue(documents.isFileModified(file))
        assertEquals("class Generated\n", Files.readString(path))
    }

    // covers: 論点16
    fun `test a2 衝突で利用者がディスクを読み直すと Document は生成物になり未保存の変更は消える`() {
        val path = root.resolve("DirtyReload.kt")
        val file = writeProvisional(path, "// provisional\n")
        val document = documents.getDocument(file) ?: throw AssertionError("no document")
        WriteCommandAction.runWriteCommandAction(project) { document.setText("// typed by the user\n") }
        val resolver = RecordingConflictResolver(reload = true)
        (documents as FileDocumentManagerImpl).setAskReloadFromDisk(testRootDisposable, resolver)

        Files.writeString(path, "class Generated\n")
        run { effects.refreshFiles(listOf(path)) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        resolver.awaitAsked("a2.reload")

        spike("a2.reload.asked", resolver.asked.map { it.name })
        spike("a2.reload.documentText", document.text.replace("\n", "\\n"))
        spike("a2.reload.isFileModified", documents.isFileModified(file))
        spike("a2.reload.vfsText", VfsUtil.loadText(file).replace("\n", "\\n"))
        assertEquals(listOf(file), resolver.asked)
        assertEquals("class Generated\n", document.text)
        assertFalse(documents.isFileModified(file))
    }

    // covers: 論点16
    fun `test a3 VfsUtil_saveText で書いた仮のファイルは保存が要らない`() {
        val path = root.resolve("feature/new/dir/ByVfs.kt")
        val file = writeProvisional(path, "// by vfs\n")

        spike("a3.saveText.isFileModified", documents.isFileModified(file))
        spike("a3.saveText.diskText", Files.readString(path))
        spike("a3.saveText.documentText", documents.getDocument(file)?.text)
        assertFalse(documents.isFileModified(file))
        assertEquals("// by vfs\n", Files.readString(path))
    }

    // covers: 論点16
    fun `test a3 Document に書いた仮のファイルは保存するまでディスクが空のまま`() {
        val path = root.resolve("feature/new/dir/ByDocument.kt")
        val file = writeProvisional(path, "")
        val document = documents.getDocument(file) ?: throw AssertionError("no document")

        WriteCommandAction.runWriteCommandAction(project) { document.setText("// by document\n") }
        spike("a3.document.isFileModified.beforeSave", documents.isFileModified(file))
        spike("a3.document.diskText.beforeSave", Files.readString(path))
        assertTrue(documents.isFileModified(file))
        assertEquals("", Files.readString(path))

        WriteAction.run<Throwable> { documents.saveDocument(document) }
        spike("a3.document.isFileModified.afterSave", documents.isFileModified(file))
        assertFalse(documents.isFileModified(file))
        assertEquals("// by document\n", Files.readString(path))
    }

    // covers: 論点16
    fun `test a3 未保存の Document に saveText で書くと同じ衝突になる`() {
        val path = root.resolve("Overwrite.kt")
        val file = writeProvisional(path, "// v1\n")
        val document = documents.getDocument(file) ?: throw AssertionError("no document")
        WriteCommandAction.runWriteCommandAction(project) { document.setText("// user typing\n") }
        val resolver = RecordingConflictResolver(reload = false)
        (documents as FileDocumentManagerImpl).setAskReloadFromDisk(testRootDisposable, resolver)

        WriteAction.run<Throwable> { VfsUtil.saveText(file, "// v2 by saveText\n") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        resolver.awaitAsked("a3.saveTextOverDirty")

        spike("a3.saveTextOverDirty.asked", resolver.asked.map { it.name })
        assertEquals(listOf(file), resolver.asked)
        assertEquals("// user typing\n", document.text)
        assertEquals("// v2 by saveText\n", Files.readString(path))
    }
}

/**
 * Answers the "File Cache Conflict" question the IDE asks when a file with unsaved changes changes on
 * disk. [reload] `null` keeps the test platform's answer (throwing), recorded in [thrown].
 */
internal class RecordingConflictResolver(private val reload: Boolean?) : MemoryDiskConflictResolver() {
    val asked: MutableList<VirtualFile> = CopyOnWriteArrayList()
    val thrown: MutableList<Throwable> = CopyOnWriteArrayList()

    override fun askReloadFromDisk(file: VirtualFile, document: Document): Boolean {
        asked += file
        if (reload != null) return reload
        return try {
            super.askReloadFromDisk(file, document)
        } catch (e: IllegalStateException) {
            thrown += e
            false
        }
    }
}
