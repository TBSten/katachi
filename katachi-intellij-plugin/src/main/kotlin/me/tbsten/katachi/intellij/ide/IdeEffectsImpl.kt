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
 */
internal class IdeEffectsImpl(private val project: Project) : IdeEffects {
    override suspend fun saveAllDocuments() {
        onEdt { writeIntentReadAction { FileDocumentManager.getInstance().saveAllDocuments() } }
    }

    override suspend fun putLocalHistoryLabel(roleNames: List<String>): String {
        val name = KatachiBundle.message("localHistory.label", roleNames.joinToString(", "))
        onEdt { LocalHistory.getInstance().putSystemLabel(project, name) }
        return name
    }

    /**
     * Refreshes from the nearest ancestor the VFS already knows, so new directories appear with
     * their files; files that were already loaded (overwritten, maybe open) are marked dirty so
     * their editors reload (spec 04). Synchronous, on a background thread.
     */
    override suspend fun refreshFiles(paths: List<Path>) {
        if (paths.isEmpty() || project.isDisposed) return
        withContext(Dispatchers.IO) {
            val fileSystem = LocalFileSystem.getInstance()
            val loaded = paths.mapNotNull { fileSystem.findFileByNioFile(it) }
            val newParents = paths.filter { fileSystem.findFileByNioFile(it) == null }.mapNotNull(::knownAncestorOf).distinct()
            if (loaded.isNotEmpty()) VfsUtil.markDirtyAndRefresh(false, false, false, *loaded.toTypedArray())
            if (newParents.isNotEmpty()) VfsUtil.markDirtyAndRefresh(false, true, true, *newParents.toTypedArray())
            paths.forEach { fileSystem.refreshAndFindFileByNioFile(it) }
        }
    }

    override suspend fun openFiles(paths: List<Path>) {
        if (paths.isEmpty()) return
        val files = withContext(Dispatchers.IO) { paths.mapNotNull { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) } }
        onEdt {
            val editors = FileEditorManager.getInstance(project)
            // Opening a file selects its tab, so open them all in order and then select the first again.
            files.forEach { editors.openFile(it, false) }
            files.firstOrNull()?.let { editors.openFile(it, true) }
        }
    }

    override suspend fun askConflict(question: ConflictQuestion): ConflictChoice =
        onEdt { ConflictDialogs.ask(project, question) } ?: ConflictChoice.Stop

    override fun openAfterGeneration(): OpenAfterGeneration = KatachiSettings.getInstance(project).openAfterGeneration

    override fun notifyGenerationFinished(report: GenerationReport) = later { KatachiNotifications.generationFinished(project, report) }

    override fun notifyLoadFailed() = later { KatachiNotifications.loadFailed(project) }

    override fun showLog() = later {
        val manager = ToolWindowManager.getInstance(project)
        (manager.getToolWindow(ToolWindowId.RUN) ?: manager.getToolWindow(BUILD_TOOL_WINDOW_ID))?.activate(null)
    }

    override fun syncGradle() = later {
        ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
    }

    override fun openDocs(page: DocsPage) = BrowserUtil.browse(docsUrlOf(page))

    override fun copyToClipboard(text: String) = CopyPasteManager.getInstance().setContents(StringSelection(text))

    /** Runs [block] on the EDT unless the project is gone; `null` when it was. */
    private suspend fun <T> onEdt(block: suspend () -> T): T? = withContext(Dispatchers.EDT) {
        if (project.isDisposed) null else block()
    }

    private fun later(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({ block() }, project.disposed)
    }
}

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
