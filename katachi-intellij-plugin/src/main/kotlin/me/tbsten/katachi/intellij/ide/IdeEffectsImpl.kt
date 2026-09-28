package me.tbsten.katachi.intellij.ide

import com.intellij.history.LocalHistory
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.generate.EntryGenerationFailure
import me.tbsten.katachi.intellij.data.generate.EntryGenerationRefusal
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.presentation.DocsPage
import me.tbsten.katachi.intellij.presentation.IdeEffects
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.awt.datatransfer.StringSelection
import java.io.IOException
import java.nio.file.Path

/**
 * [IdeEffects] over the IntelliJ API. Every effect is dropped once [project] is disposed (E-49), and
 * UI work hops to the EDT itself, so the ViewModel may call from any thread.
 *
 * Every SDK call goes through [sdkCall]: an effect the IDE failed at is logged and reported through
 * the return value where the screen shows it (no label, files not opened, "stop" at a conflict),
 * and never reaches the ViewModel as an exception, which would turn a finished generation into a
 * failed one.
 */
internal class IdeEffectsImpl(
    private val project: Project,
    /**
     * Runs first inside the SDK calls of the generation from an entry, with [EntrySdkCall] naming which;
     * a test throws from it to play the IDE failing there. Does nothing in the IDE.
     */
    private val entrySdkProbe: (EntrySdkCall) -> Unit = {},
) : IdeEffects {
    override suspend fun saveAllDocuments() {
        onEdt { sdkCall("save all documents") { writeIntentReadAction { FileDocumentManager.getInstance().saveAllDocuments() } } }
    }

    override suspend fun putLocalHistoryLabel(titles: List<String>): String? {
        val name = KatachiBundle.message("localHistory.label", titles.joinToString(", "))
        val put = onEdt { sdkCall("put the Local History label \"$name\"") { LocalHistory.getInstance().putSystemLabel(project, name) } }
        return name.takeIf { put?.isSuccess == true }
    }

    /**
     * Refreshes from the nearest ancestor the VFS already knows, so new directories appear with
     * their files; files that were already loaded (overwritten, maybe open) are marked dirty so
     * their editors reload (spec 04). Synchronous, on a background thread. A refresh the IDE fails
     * at leaves the files as the VFS knew them; [openFiles] finds them anyway.
     */
    override suspend fun refreshFiles(paths: List<Path>) {
        if (paths.isEmpty() || project.isDisposed) return
        withContext(Dispatchers.IO) {
            sdkCall("refresh ${paths.size} generated files in the VFS") {
                val fileSystem = LocalFileSystem.getInstance()
                val loaded = paths.mapNotNull { fileSystem.findFileByNioFile(it) }
                val newParents = paths.filter { fileSystem.findFileByNioFile(it) == null }.mapNotNull(::knownAncestorOf).distinct()
                if (loaded.isNotEmpty()) VfsUtil.markDirtyAndRefresh(false, false, false, *loaded.toTypedArray())
                if (newParents.isNotEmpty()) VfsUtil.markDirtyAndRefresh(false, true, true, *newParents.toTypedArray())
                paths.forEach { fileSystem.refreshAndFindFileByNioFile(it) }
            }
        }
    }

    override suspend fun openFiles(paths: List<Path>): List<Path> {
        if (paths.isEmpty()) return emptyList()
        val files = withContext(Dispatchers.IO) {
            paths.mapNotNull { path ->
                sdkCall("find $path in the VFS") { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) }.getOrNull()?.let { path to it }
            }
        }
        return onEdt {
            val editors = sdkCall("get the editors") { FileEditorManager.getInstance(project) }.getOrNull() ?: return@onEdt emptyList()
            // Opening a file selects its tab, so open them all in order and then select the first again.
            val opened = files.filter { (path, file) -> sdkCall("open $path in an editor") { editors.openFile(file, false) }.isSuccess }
            opened.firstOrNull()?.let { (path, file) -> sdkCall("select $path") { editors.openFile(file, true) } }
            opened.map { (path, _) -> path }
        }.orEmpty()
    }

    override suspend fun askConflict(question: ConflictQuestion): ConflictChoice =
        onEdt { ConflictDialogs.ask(project, question) } ?: ConflictChoice.Stop

    override fun openAfterGeneration(): OpenAfterGeneration =
        sdkCall("read the katachi settings") { KatachiSettings.getInstance(project).openAfterGeneration }.getOrDefault(OpenAfterGeneration.First)

    override fun notifyGenerationFinished(report: GenerationReport) = later("notify that the generation finished") {
        KatachiNotifications.generationFinished(project, report)
    }

    override fun notifyLoadFailed() = later("notify that loading failed") { KatachiNotifications.loadFailed(project) }

    override fun showLog() = later("show the Gradle log") {
        val manager = ToolWindowManager.getInstance(project)
        (manager.getToolWindow(ToolWindowId.RUN) ?: manager.getToolWindow(BUILD_TOOL_WINDOW_ID))?.activate(null)
    }

    override fun syncGradle() = later("sync the Gradle project") {
        ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
    }

    override fun openDocs(page: DocsPage) {
        sdkCall("open the documentation") { BrowserUtil.browse(docsUrlOf(page)) }
    }

    override fun copyToClipboard(text: String) {
        sdkCall("copy to the clipboard") { CopyPasteManager.getInstance().setContents(StringSelection(text)) }
    }

    override suspend fun createDirectories(directory: Path): Boolean = onEdt {
        sdkCall("create the directory $directory") {
            entrySdkProbe(EntrySdkCall.WriteAction)
            writeIntentReadAction {
                WriteAction.compute<VirtualFile?, IOException> { VfsUtil.createDirectoryIfMissing(directory.toString()) }
            } ?: throw IOException("The VFS did not create $directory")
        }.isSuccess
    } == true

    /**
     * Writes through the VFS (`VfsUtil.saveText`), so the file is saved at once and its Document, if open,
     * follows without becoming unsaved (S2 (a3)). Opens it whatever "open after generation" says
     * (decision 13); a file the editor failed to open is still written, and a balloon says so.
     */
    override suspend fun writeProvisionalFile(path: Path, text: String): Boolean {
        val file = onEdt {
            sdkCall("write the provisional file $path") {
                entrySdkProbe(EntrySdkCall.WriteAction)
                writeIntentReadAction { WriteAction.compute<VirtualFile, IOException> { writeFile(path, text) } }
            }.getOrNull()
        } ?: return false
        val opened = onEdt {
            sdkCall("open $path in an editor") {
                entrySdkProbe(EntrySdkCall.Editor)
                FileEditorManager.getInstance(project).openFile(file, true)
            }.isSuccess
        }
        if (opened == false) warn(KatachiBundle.message("generate.failed.open", path.fileName.toString()))
        return true
    }

    /** Call inside a write action. */
    private fun writeFile(path: Path, text: String): VirtualFile {
        val directory = VfsUtil.createDirectoryIfMissing(path.parent.toString()) ?: throw IOException("The VFS did not create ${path.parent}")
        val name = path.fileName.toString()
        val file = directory.findChild(name) ?: directory.createChildData(this, name)
        VfsUtil.saveText(file, text)
        return file
    }

    /** The open Document's text when there is one (unsaved edits included), else the disk's (decision 12). */
    override suspend fun currentText(path: Path): CharSequence? = withContext(Dispatchers.IO) {
        val shown = sdkCall("read the Document of $path") {
            readAction { cachedDocumentOf(path)?.immutableCharSequence }
        }.getOrNull()
        shown ?: NioProjectFileSystem.readText(path)
    }

    /** `true` when the IDE cannot tell: a generation then stops rather than overwrite what may be unsaved. */
    override suspend fun hasUnsavedChanges(path: Path): Boolean = withContext(Dispatchers.IO) {
        sdkCall("ask whether $path has unsaved changes") {
            readAction { cachedDocumentOf(path)?.let { FileDocumentManager.getInstance().isDocumentUnsaved(it) } == true }
        }.getOrDefault(true)
    }

    override suspend fun saveDocument(path: Path): Boolean = onEdt {
        sdkCall("save $path") {
            writeIntentReadAction { cachedDocumentOf(path)?.let { FileDocumentManager.getInstance().saveDocument(it) } }
        }.isSuccess
    } == true

    override suspend fun packageNameOf(directory: Path): String? = withContext(Dispatchers.IO) {
        sdkCall("find the package of $directory") { readAction { packageOfDirectory(project, directory) } }.getOrNull()
    }

    /**
     * Makes each open Document show what Gradle wrote even when the VFS missed the change (same length and
     * time, S2 (a)). A Document with unsaved edits is left to the platform, which asks the user.
     */
    override suspend fun reloadFromDisk(paths: List<Path>) {
        if (paths.isEmpty()) return
        onEdt {
            val documents = FileDocumentManager.getInstance()
            for (path in paths) {
                sdkCall("reload $path from disk") {
                    val document = cachedDocumentOf(path) ?: return@sdkCall
                    val disk = NioProjectFileSystem.readText(path) ?: return@sdkCall
                    if (!documents.isDocumentUnsaved(document) && !document.immutableCharSequence.contentEquals(disk)) {
                        writeIntentReadAction { documents.reloadFromDisk(document, project) }
                    }
                }
            }
        }
    }

    /** Always shown, the tool window or not: the user started it from the editor or the New menu. */
    override fun notifyEntryGenerationFailed(failure: EntryGenerationFailure) = warn(entryFailureText(failure))

    override fun notifyEntryGenerationRefused(refusal: EntryGenerationRefusal) = warn(entryRefusalText(refusal))

    override fun provisionalNotice(templateTitle: String): String =
        KatachiBundle.message("generate.provisional.header", templateTitle) + "\n" + KatachiBundle.message("generate.provisional.command")

    private fun warn(text: String) = later("show the balloon \"$text\"") {
        NotificationGroupManager.getInstance().getNotificationGroup(KatachiNotifications.GROUP_ID)
            .createNotification(text, NotificationType.WARNING)
            .notify(project)
    }

    /** Runs [block] on the EDT unless the project is gone; `null` when it was. */
    private suspend fun <T> onEdt(block: suspend () -> T): T? = withContext(Dispatchers.EDT) {
        if (project.isDisposed) null else block()
    }

    /** Runs [block], which does [action], on the EDT later, unless the project is gone by then. */
    private fun later(action: String, block: () -> Unit) {
        sdkCall("schedule: $action") {
            ApplicationManager.getApplication().invokeLater({ sdkCall(action, block) }, project.disposed)
        }
    }
}

/** Which SDK call of the generation from an entry [IdeEffectsImpl]'s probe is in. */
internal enum class EntrySdkCall {
    /** Creating the directories and writing the provisional file. */
    WriteAction,

    /** `FileEditorManager`: opening the provisional file. */
    Editor,
}

/** Call in a read action. The Document the IDE holds for [path], if any; loads nothing. */
private fun cachedDocumentOf(path: Path): Document? =
    LocalFileSystem.getInstance().findFileByNioFile(path)?.let { FileDocumentManager.getInstance().getCachedDocument(it) }

/** Called inside [sdkCall]. */
private fun knownAncestorOf(path: Path): VirtualFile? {
    val fileSystem = LocalFileSystem.getInstance()
    var current: Path? = path.parent
    while (current != null) {
        fileSystem.findFileByNioFile(current)?.let { return it }
        current = current.parent
    }
    return null
}

/** Provisional: the Japanese pages, as the tool window speaks Japanese. There is no "update" page yet. */
internal fun docsUrlOf(page: DocsPage): String = DOCS_ROOT + when (page) {
    DocsPage.WritingTemplates -> "ja/guides/generate-code-from-template/"
    DocsPage.Install, DocsPage.Update -> "ja/get-started/first-architecture/"
}

private const val DOCS_ROOT = "https://tbsten.github.io/katachi/"

// ToolWindowId has no constant for the Build tool window.
private const val BUILD_TOOL_WINDOW_ID = "Build"
