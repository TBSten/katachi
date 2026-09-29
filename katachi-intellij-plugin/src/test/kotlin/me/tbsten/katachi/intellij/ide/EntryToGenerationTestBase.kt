package me.tbsten.katachi.intellij.ide

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EditorTestUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.ThrowableRunnable
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.EditorNotificationsImpl
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.detect.detectDefinitionModules
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.data.generate.entryTargetOf
import me.tbsten.katachi.intellij.ide.dialog.GenerateDialogs
import me.tbsten.katachi.intellij.ide.notification.EditorNotificationMemoryService
import me.tbsten.katachi.intellij.ide.notification.KatachiEditorNotificationProvider
import me.tbsten.katachi.intellij.ide.notification.NotificationPorts
import me.tbsten.katachi.intellij.ide.notification.entrySettingsOf
import me.tbsten.katachi.intellij.model.DetectionResult
import me.tbsten.katachi.intellij.model.templatesOf
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

/**
 * Nothing faked but Gradle and the modal dialog: the real notification provider and New group, the real
 * project service with its real `EntryEffectsImpl`, the real VFS, editors and Documents, and T1's
 * [DiskGradleRunner]. The dialog answers through `GenerateDialogs.setTestAnswer`, with the request a
 * dialog would hand over for the values it was opened with ([answerWith]).
 *
 * The fixture is [GenerationFromEntryTestBase]'s: `sample-jvm-with-captures` in `:arch-a`, whose
 * directory is katachi's project root.
 */
internal abstract class EntryToGenerationTestBase : GenerationFromEntryTestBase() {
    /** The dialogs opened, in order, as the entry asked for them. */
    protected val dialogs: MutableList<GenerateDialogRequest> = Collections.synchronizedList(mutableListOf())

    private lateinit var memoryService: EditorNotificationMemoryService
    private val notifications get() = EditorNotifications.getInstance(project) as EditorNotificationsImpl

    private var savedNotificationSettings = listOf<Boolean>()

    override fun setUp() {
        super.setUp()
        val settings = KatachiSettings.getInstance(project)
        savedNotificationSettings = listOf(settings.editorNotificationEnabled, settings.notifyOnEmptyFile, settings.notifyOnFileWithContent, settings.notifyOnContentOnce)
        addSourceRoot(sourceRoot)
        install()
        memoryService = EditorNotificationMemoryService(project, scope).also { Disposer.register(testRootDisposable, it) }
        val provider = KatachiEditorNotificationProvider { ports() }
        @Suppress("UNCHECKED_CAST")
        (EditorNotificationProvider.EP_NAME.getPoint(project) as ExtensionPointImpl<EditorNotificationProvider>).maskAll(listOf(provider), testRootDisposable, true)
    }

    override fun tearDown() {
        try {
            val settings = KatachiSettings.getInstance(project)
            settings.editorNotificationEnabled = savedNotificationSettings[0]
            settings.notifyOnEmptyFile = savedNotificationSettings[1]
            settings.notifyOnFileWithContent = savedNotificationSettings[2]
            settings.notifyOnContentOnce = savedNotificationSettings[3]
        } finally {
            super.tearDown()
        }
    }

    /** The notification also on files with content, every time: what makes a generated file's notification observable. */
    protected fun notifyOnEveryFileWithContent() {
        val settings = KatachiSettings.getInstance(project)
        settings.editorNotificationEnabled = true
        settings.notifyOnFileWithContent = true
        settings.notifyOnContentOnce = false
    }

    // Opening an editor starts the bundled Vue LSP support, which logs an error in this IDE build (S2 (b)).
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        ignoreUnrelatedLoggedErrors { super.runTestRunnable(testRunnable) }
    }

    private fun ports(): NotificationPorts = NotificationPorts(
        existingService = { service },
        hasDefinitionModule = { detectDefinitionModules(synced).let { it is DetectionResult.Found || it is DetectionResult.TaskListMissing } },
        service = { service },
        settings = { entrySettingsOf(KatachiSettings.getInstance(project)) },
        memory = { memoryService },
    )

    /**
     * Answers every dialog with what pressing [Generate] would hand over: the values it opened with
     * (the captures the origin decided) plus [entered] typed by the user, the target from the same rule the dialog showed.
     * [beforeAnswer] runs first, as the time the dialog is open does.
     */
    protected fun answerWith(vararg entered: Pair<String, String>, beforeAnswer: () -> Unit = {}) {
        GenerateDialogs.setTestAnswer(
            { opened ->
                dialogs += opened
                beforeAnswer()
                val template = templatesOf(service.viewModel.state.value.snapshots).first { it.id == opened.initialTemplate }
                val args = (opened.seeds + entered).toList()
                val undecided = SingleFileGenerationRequest(template, opened.origin, args, null)
                undecided.copy(target = entryTargetOf(template, undecided, NioProjectFileSystem))
            },
            testRootDisposable,
        )
    }

    /** Writes [text] at [relative] under the project root (the definition's directory) and returns it in the VFS. */
    protected fun writeFile(path: Path, text: String): VirtualFile {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw AssertionError("Not in the VFS: $path")
    }

    /** Opens [file] in an editor, loaded, as the platform needs for notifications. */
    protected fun open(file: VirtualFile): FileEditor {
        val editor = FileEditorManager.getInstance(project).openFile(file, true).single()
        EditorTestUtil.waitForLoading((editor as TextEditor).editor)
        return editor
    }

    /** katachi's panel on [editor] once the pending updates are done; `null` when there is none. */
    protected fun panelOf(editor: FileEditor): EditorNotificationPanel? {
        settleNotifications()
        return notifications.getNotificationPanels(editor).entries
            .firstOrNull { it.key == KatachiEditorNotificationProvider::class.java }?.value as EditorNotificationPanel?
    }

    /** The panel, waiting for the load and the update the open started. */
    protected fun waitForPanel(editor: FileEditor): EditorNotificationPanel {
        var panel: EditorNotificationPanel? = null
        PlatformTestUtil.waitWithEventsDispatching({ "the katachi notification did not come" }, { panel = panelOf(editor); panel != null }, 10)
        return panel!!
    }

    protected fun updateAllNotifications() {
        notifications.updateAllNotifications()
        settleNotifications()
    }

    private fun settleNotifications() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        notifications.completeAsyncTasks()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    /** Pumps the EDT until [path]'s generation has ended, and returns how. */
    protected fun awaitLedger(path: Path): LedgerEntry {
        waitUntil("the generation of $path ends") { service.ledger.entryOf(path).let { it is LedgerEntry.Succeeded || it is LedgerEntry.Failed } }
        return service.ledger.entryOf(path)!!
    }

    /** The directory at [path] (created) as the Project view hands it to New. */
    protected fun directoryOf(path: Path): PsiDirectory {
        Files.createDirectories(path)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw AssertionError("Not in the VFS: $path")
        return PsiManager.getInstance(project).findDirectory(file) ?: throw AssertionError("No PsiDirectory for $path")
    }

    /** The event New › katachi's `update` and `getChildren` get for [directory]. */
    protected fun menuEventOn(action: AnAction, directory: PsiDirectory): AnActionEvent {
        val view = object : IdeView {
            override fun getDirectories(): Array<PsiDirectory> = arrayOf(directory)
            override fun getOrChooseDirectory(): PsiDirectory? = throw AssertionError("New > katachi must not ask the IdeView to choose a directory")
        }
        val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(LangDataKeys.IDE_VIEW, view).build()
        return TestActionEvent.createTestEvent(action, context)
    }

    /** The leaves of [this], submenus opened. */
    protected fun Array<AnAction>.flattened(): List<AnAction> =
        flatMap { if (it is ActionGroup) it.getChildren(null).flattened() else listOf(it) }

    /** The kinds of the Gradle runs so far: `json` for a load of the definitions, `generate` for `katachiTemplate`. */
    protected fun gradleKinds(): List<String> =
        gradle.requests.map { if (it.tasks.single().taskPath.endsWith(":katachiTemplate")) "generate" else "json" }
}
