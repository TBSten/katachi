package me.tbsten.katachi.intellij.ide.spike

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.EditorNotificationsImpl
import kotlinx.coroutines.async
import me.tbsten.katachi.intellij.ide.IdeEffectsImpl
import me.tbsten.katachi.intellij.ide.KatachiIdeTestBase
import com.intellij.util.ThrowableRunnable
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Function
import javax.swing.JComponent

/** S2 (b)(c): the editor notification over a real file of LocalFileSystem. */
internal class NotificationSpikeTest : KatachiIdeTestBase() {
    private lateinit var provider: RecordingProvider
    private val notifications get() = EditorNotifications.getInstance(project) as EditorNotificationsImpl

    override fun setUp() {
        super.setUp()
        provider = RecordingProvider()
        // Only ours: the bundled Vue plugin's provider throws NoClassDefFoundError in this IDE build, an Error
        // that cancels the whole update job before later providers run (run9 log). maskAll replaces the list.
        @Suppress("UNCHECKED_CAST")
        (EditorNotificationProvider.EP_NAME.getPoint(project) as ExtensionPointImpl<EditorNotificationProvider>).maskAll(listOf(provider), testRootDisposable, true)
    }

    // Opening an editor starts the bundled Vue LSP support, which logs an error in this IDE build; only
    // tearDown is wrapped by AnalysisTestBase, so the test body is wrapped here too.
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        ignoreUnrelatedLoggedErrors { super.runTestRunnable(testRunnable) }
    }

    private fun openReal(name: String, text: String): Pair<VirtualFile, FileEditor> {
        val path = root.resolve("feature/src/main/kotlin/$name")
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw AssertionError("not in VFS")
        val editors = FileEditorManager.getInstance(project).openFile(file, true)
        spike("b.editors", editors.map { it.javaClass.name })
        val editor = editors.single()
        val textEditor = (editor as TextEditor).editor
        spike("b.headlessEnvironment", ApplicationManager.getApplication().isHeadlessEnvironment)
        spike("b.loadedRightAfterOpen", AsyncEditorLoader.isEditorLoaded(textEditor))
        // The platform computes notifications only for loaded text editors (EditorNotificationsImpl.getEditors).
        EditorTestUtil.waitForLoading(textEditor)
        spike("b.loadedAfterWait", AsyncEditorLoader.isEditorLoaded(textEditor))
        return file to editor
    }

    /** The panel after the pending updates; asks for one for [file] first when opening did not compute any. */
    private fun panelOf(editor: FileEditor, file: VirtualFile? = null): JComponent? {
        settle()
        if (file != null) {
            spike("b.callsAfterOpenOnly", provider.calls.size)
            spike("b.fileEditorManager", FileEditorManager.getInstance(project).javaClass.name)
            spike("b.allEditorList", FileEditorManager.getInstance(project).getAllEditorList(file).size)
            if (provider.calls.isEmpty()) {
                notifications.updateNotifications(file)
                settle()
                spike("b.callsAfterExplicitUpdate", provider.calls.size)
            }
        }
        return notifications.getNotificationPanels(editor).entries.firstOrNull { it.key == RecordingProvider::class.java }?.value
    }

    /** updateNotifications launches on the EDT, so pump it before waiting for the jobs it starts. */
    private fun settle() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        notifications.completeAsyncTasks()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    // covers: 論点13
    fun `test b 実在するファイルを開くと通知が出て collectNotificationData は BGT で読み取りロックを持って Document を読める`() {
        val (file, editor) = openReal("SpikeEmpty.kt", "package com.example\n")

        val panel = panelOf(editor, file)
        spike("b.panels", notifications.getNotificationPanels(editor).keys.map { it.name })
        spike("b.calls", provider.calls)
        spike("b.panelText", (panel as? EditorNotificationPanel)?.text)

        assertTrue("collectNotificationData was called", provider.calls.isNotEmpty())
        val call = provider.calls.last()
        assertFalse("not on the EDT", call.edt)
        assertTrue("read access", call.readAllowed)
        assertNull(call.error)
        assertEquals("package com.example\n", call.document)
        assertTrue(panel is EditorNotificationPanel)
        assertEquals("spike: 20", (panel as EditorNotificationPanel).text)
        spike("b.functionAppliedOnEdt", provider.appliedOnEdt)
        assertEquals(listOf(true), provider.appliedOnEdt.distinct())
    }

    // covers: 論点13
    fun `test b 通知のリンクを doClick で押せる`() {
        val (file, editor) = openReal("SpikeLink.kt", "")

        val panel = panelOf(editor, file) as EditorNotificationPanel
        panel.findLabelByName("Create")?.doClick() ?: throw AssertionError("no link")

        assertEquals(1, provider.clicked)
    }

    // covers: 論点13
    fun `test b collectNotificationData が null を返すファイルには通知が出ない`() {
        val (file, editor) = openReal("Other.kt", "")

        assertNull(panelOf(editor, file))
    }

    // covers: 論点13
    fun `test c 保存していない Document の編集では通知は再計算されない`() {
        val (file, editor) = openReal("SpikeEdit.kt", "")
        panelOf(editor, file)
        val before = provider.calls.size
        val document = FileDocumentManager.getInstance().getDocument(file) ?: throw AssertionError("no document")

        WriteCommandAction.runWriteCommandAction(project) { document.setText("class Typed\n") }
        val panel = panelOf(editor) as EditorNotificationPanel

        spike("c.callsBefore", before)
        spike("c.callsAfterEdit", provider.calls.size)
        spike("c.panelTextAfterEdit", panel.text)
        assertEquals(before, provider.calls.size)
        assertEquals("spike: 0", panel.text)
    }

    // covers: 論点13
    fun `test c 編集の後に updateNotifications を呼ぶと未保存の中身で再計算される`() {
        val (file, editor) = openReal("SpikeUpdate.kt", "")
        panelOf(editor, file)
        val document = FileDocumentManager.getInstance().getDocument(file) ?: throw AssertionError("no document")

        WriteCommandAction.runWriteCommandAction(project) { document.setText("class Typed\n") }
        notifications.updateNotifications(file)
        val panel = panelOf(editor) as EditorNotificationPanel

        spike("c.update.lastCall", provider.calls.last())
        assertEquals("class Typed\n", provider.calls.last().document)
        assertEquals("class Typed\n", provider.calls.last().cached)
        assertEquals("spike: 12", panel.text)
        assertTrue(FileDocumentManager.getInstance().isFileModified(file))
    }

    // covers: 論点13
    fun `test c 外から書き換わって refreshFiles で読み直しても通知は再計算されない`() {
        val (file, editor) = openReal("SpikeExternal.kt", "")
        panelOf(editor, file)
        val before = provider.calls.size
        val path = file.toNioPath()

        Files.writeString(path, "class Generated\n")
        val deferred = scope.async { IdeEffectsImpl(project).refreshFiles(listOf(path)) }
        PlatformTestUtil.waitWithEventsDispatching("refresh", { deferred.isCompleted }, 10)
        val panel = panelOf(editor) as EditorNotificationPanel

        spike("c.external.callsBefore", before)
        spike("c.external.callsAfter", provider.calls.size)
        spike("c.external.documentText", FileDocumentManager.getInstance().getDocument(file)?.text)
        assertEquals("class Generated\n", FileDocumentManager.getInstance().getDocument(file)?.text)
        assertEquals(before, provider.calls.size)
        assertEquals("spike: 0", panel.text)
    }

    // covers: 論点13
    fun `test c 保存すると通知は再計算されるか`() {
        val (file, editor) = openReal("SpikeSave.kt", "")
        panelOf(editor, file)
        val before = provider.calls.size
        val document = FileDocumentManager.getInstance().getDocument(file) ?: throw AssertionError("no document")

        WriteCommandAction.runWriteCommandAction(project) { document.setText("class Saved\n") }
        ApplicationManager.getApplication().runWriteAction { FileDocumentManager.getInstance().saveDocument(document) }
        panelOf(editor)

        spike("c.save.callsBefore", before)
        spike("c.save.callsAfter", provider.calls.size)
    }
}

/** Shows a panel for files named `Spike*`, recording where and what it could read. */
internal class RecordingProvider : EditorNotificationProvider, DumbAware {
    data class Call(val edt: Boolean, val readAllowed: Boolean, val cached: String?, val document: String?, val error: Throwable?)

    val calls: MutableList<Call> = CopyOnWriteArrayList()
    val appliedOnEdt: MutableList<Boolean> = CopyOnWriteArrayList()

    @Volatile var clicked: Int = 0

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!file.name.startsWith("Spike")) return null
        val app = ApplicationManager.getApplication()
        val documents = FileDocumentManager.getInstance()
        val call = try {
            val cached = documents.getCachedDocument(file)?.charsSequence?.toString()
            val document = documents.getDocument(file)?.charsSequence?.toString()
            Call(app.isDispatchThread, app.isReadAccessAllowed, cached, document, null)
        } catch (e: Throwable) {
            Call(app.isDispatchThread, app.isReadAccessAllowed, null, null, e)
        }
        calls += call
        return Function { editor ->
            appliedOnEdt += ApplicationManager.getApplication().isDispatchThread
            EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).apply {
                text = "spike: ${call.document?.length}"
                createActionLabel("Create") { clicked++ }
            }
        }
    }
}
