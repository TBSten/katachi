package me.tbsten.katachi.intellij.ide

import com.intellij.history.LocalHistory
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
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
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.presentation.DocsPage
import me.tbsten.katachi.intellij.presentation.IdeEffects
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.awt.datatransfer.StringSelection
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
internal class IdeEffectsImpl(private val project: Project) : IdeEffects {
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
